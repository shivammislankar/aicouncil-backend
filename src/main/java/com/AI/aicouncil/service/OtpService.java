package com.AI.aicouncil.service;

import com.AI.aicouncil.model.EmailVerification;
import com.AI.aicouncil.repository.EmailVerificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Email OTP: proves an address can receive mail before the account may use
 * the council. A 6-digit code is generated, only its SHA-256 hash is stored,
 * and the plaintext code travels by email only.
 *
 * <p>Everything is a no-op while {@code otp.enabled=false}, so the app can be
 * deployed before the Gmail credentials exist without breaking sign-ups.</p>
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailVerificationRepository repository;
    private final JavaMailSender mailSender;

    private final boolean enabled;
    private final long ttlSeconds;
    private final int maxAttempts;
    private final String from;
    private final int maxRequestsPerWindow;
    private final long resendWindowSeconds;

    public OtpService(
            EmailVerificationRepository repository,
            JavaMailSender mailSender,
            @Value("${otp.enabled:false}") boolean enabled,
            @Value("${otp.ttl-seconds:600}") long ttlSeconds,
            @Value("${otp.max-attempts:5}") int maxAttempts,
            @Value("${otp.from:}") String from,
            @Value("${otp.max-requests-per-window:3}") int maxRequestsPerWindow,
            @Value("${otp.resend-window-seconds:600}") long resendWindowSeconds) {
        this.repository = repository;
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.ttlSeconds = ttlSeconds;
        this.maxAttempts = maxAttempts;
        this.from = from;
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.resendWindowSeconds = resendWindowSeconds;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Normalises for storage/lookup: one row per address, case-insensitive. */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    @Transactional(readOnly = true)
    public boolean isVerified(String email) {
        if (!enabled) return true;
        if (email == null) return false;
        return repository.findByEmail(normalize(email))
                .map(EmailVerification::isVerified)
                .orElse(false);
    }

    /** Marks an address verified without a code (verified OAuth providers). */
    @Transactional
    public void markVerified(String email) {
        String key = normalize(email);
        if (key == null) return;
        EmailVerification row = repository.findByEmail(key)
                .orElseGet(() -> new EmailVerification(key));
        row.setVerified(true);
        row.setCodeHash(null);
        row.setCodeExpiresAt(null);
        row.touch();
        repository.save(row);
    }

    /**
     * Generates a fresh code and emails it.
     *
     * @throws OtpNotConfiguredException when {@code otp.enabled=false}
     * @throws TooManyRequestsException  when the resend budget is exhausted
     * @throws MailException             when Gmail refuses the send
     */
    @Transactional
    public void requestCode(String email) {
        if (!enabled) throw new OtpNotConfiguredException();
        String key = normalize(email);

        EmailVerification row = repository.findByEmail(key)
                .orElseGet(() -> new EmailVerification(key));

        Instant now = Instant.now();
        if (row.getWindowStart() == null
                || now.isAfter(row.getWindowStart().plusSeconds(resendWindowSeconds))) {
            row.setWindowStart(now);
            row.setRequestCount(0);
        }
        if (row.getRequestCount() >= maxRequestsPerWindow) {
            throw new TooManyRequestsException();
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        row.setCodeHash(sha256Hex(code));
        row.setCodeExpiresAt(now.plusSeconds(ttlSeconds));
        row.setAttempts(0);
        row.setRequestCount(row.getRequestCount() + 1);
        row.touch();
        repository.save(row);

        sendEmail(key, code);
        // Never log the code: logs are the easiest place for one to leak.
        log.info("OTP issued for {}", mask(key));
    }

    /**
     * Checks a submitted code.
     *
     * @return true when the address just became (or already was) verified
     * @throws OtpNotConfiguredException when {@code otp.enabled=false}
     * @throws NoActiveCodeException     when no live code exists for the address
     * @throws TooManyRequestsException  when the guess budget is exhausted
     */
    @Transactional
    public boolean verifyCode(String email, String code) {
        if (!enabled) throw new OtpNotConfiguredException();
        String key = normalize(email);

        EmailVerification row = repository.findByEmail(key)
                .orElseThrow(NoActiveCodeException::new);
        if (row.isVerified()) return true;

        Instant now = Instant.now();
        if (row.getCodeHash() == null
                || row.getCodeExpiresAt() == null
                || now.isAfter(row.getCodeExpiresAt())) {
            row.setCodeHash(null);
            row.setCodeExpiresAt(null);
            row.touch();
            repository.save(row);
            throw new NoActiveCodeException();
        }
        if (row.getAttempts() >= maxAttempts) {
            // Burn the code: guessing must not continue indefinitely.
            row.setCodeHash(null);
            row.setCodeExpiresAt(null);
            row.touch();
            repository.save(row);
            throw new TooManyRequestsException();
        }

        String candidate = code == null ? "" : code.trim();
        if (constantTimeEquals(row.getCodeHash(), sha256Hex(candidate))) {
            row.setVerified(true);
            row.setCodeHash(null);
            row.setCodeExpiresAt(null);
            row.touch();
            repository.save(row);
            log.info("Email verified: {}", mask(key));
            return true;
        }

        row.setAttempts(row.getAttempts() + 1);
        row.touch();
        repository.save(row);
        return false;
    }

    private void sendEmail(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Your Veritas verification code");
        message.setText(
                "Your Veritas verification code is:\n\n"
                        + "    " + code + "\n\n"
                        + "It expires in 10 minutes. If you didn't create a Veritas account, "
                        + "ignore this email — no account will be activated without the code.");
        mailSender.send(message);
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Hashes are compared without early exit so timing reveals nothing. */
    static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        if (x.length != y.length) return false;
        int diff = 0;
        for (int i = 0; i < x.length; i++) {
            diff |= x[i] ^ y[i];
        }
        return diff == 0;
    }

    static String mask(String email) {
        if (email == null) return "?";
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }

    public static class OtpNotConfiguredException extends RuntimeException {
    }

    public static class NoActiveCodeException extends RuntimeException {
    }

    public static class TooManyRequestsException extends RuntimeException {
    }
}
