package com.AI.aicouncil.controller;

import com.AI.aicouncil.security.DailyUsageQuota;
import com.AI.aicouncil.security.FirebaseUserService;
import com.AI.aicouncil.security.UserRateLimiter;
import com.AI.aicouncil.service.OtpService;
import com.google.firebase.auth.FirebaseToken;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Email OTP verification and OAuth provider sync.
 *
 * <p>Every endpoint requires a Firebase session, and the address involved is
 * always the one inside the caller's verified ID token. The request body is
 * never trusted for an email address, so nobody can make this server mail an
 * arbitrary inbox or verify someone else's address.</p>
 *
 * <p>Successful verification is written to Firebase ({@code emailVerified}),
 * which survives redeploys; the local database only holds short-lived codes.</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final OtpService otpService;
    private final UserRateLimiter rateLimiter;
    private final FirebaseUserService firebaseUsers;
    /** Site-wide cap on outgoing verification emails (protects the Gmail account's sending limit). */
    private final DailyUsageQuota emailQuota;

    public AuthController(OtpService otpService,
                          UserRateLimiter rateLimiter,
                          FirebaseUserService firebaseUsers,
                          @Value("${otp.max-emails-per-day:300}") int maxEmailsPerDay) {
        this.otpService = otpService;
        this.rateLimiter = rateLimiter;
        this.firebaseUsers = firebaseUsers;
        // Per-user: 10 emails/day per account; global: maxEmailsPerDay.
        this.emailQuota = new DailyUsageQuota(10, maxEmailsPerDay);
    }

    public record OtpVerifyRequest(
            @NotBlank @Pattern(regexp = "\\s*\\d{6}\\s*", message = "Enter the 6-digit code from the email.")
            String code) {
    }

    /** Email a fresh 6-digit code to the signed-in account's own address. */
    @PostMapping("/otp/request")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> requestCode() {
        if (!otpService.isEnabled()) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "Email verification is not configured yet. Please try again later."));
        }

        FirebaseToken user = currentUser();
        String email = user.getEmail();
        if (email == null) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "Your sign-in has no email address to verify."));
        }
        if (otpService.isVerified(email, user.isEmailVerified())) {
            return ResponseEntity.ok(Map.of("verified", true, "message", "Your email is already verified."));
        }

        String key = "otp:" + user.getUid();
        if (!rateLimiter.tryAcquire(key)) {
            return ResponseEntity.status(429)
                    .header("Retry-After", String.valueOf(rateLimiter.retryAfterSeconds(key)))
                    .body(Map.of("error", "Too many code requests. Please wait a moment and try again."));
        }
        if (emailQuota.tryConsume(user.getUid()) != DailyUsageQuota.Result.ALLOWED) {
            return ResponseEntity.status(429)
                    .header("Retry-After", String.valueOf(emailQuota.secondsUntilReset()))
                    .body(Map.of("error", "We've sent the maximum number of codes for today. Please try again tomorrow."));
        }

        try {
            otpService.requestCode(email);
        } catch (OtpService.TooManyRequestsException e) {
            emailQuota.refund(user.getUid());
            return ResponseEntity.status(429).body(Map.of(
                    "error", "Too many codes requested. New codes are available again in 10 minutes."));
        } catch (Exception e) {
            emailQuota.refund(user.getUid());
            // Mail failures stay generic: the cause (bad credentials, quota) is
            // ours to fix, not the visitor's to debug.
            return ResponseEntity.status(502).body(Map.of(
                    "error", "Could not send the code right now. Please try again in a minute."));
        }

        return ResponseEntity.ok(Map.of("message", "A code is on its way to your inbox."));
    }

    /** Check the code for the signed-in account's own address. */
    @PostMapping("/otp/verify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> verifyCode(@Valid @RequestBody OtpVerifyRequest request) {
        FirebaseToken user = currentUser();
        String email = user.getEmail();

        if (!otpService.isEnabled() || user.isEmailVerified()) {
            return ResponseEntity.ok(Map.of("verified", true));
        }
        if (email == null) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "Your sign-in has no email address to verify."));
        }

        try {
            boolean ok = otpService.verifyCode(email, request.code());
            if (ok) {
                firebaseUsers.markEmailVerified(user.getUid());
                return ResponseEntity.ok(Map.of("verified", true));
            }
            return ResponseEntity.status(400).body(Map.of(
                    "verified", false, "error", "That code doesn't match. Check the latest email and try again."));
        } catch (OtpService.NoActiveCodeException e) {
            return ResponseEntity.status(410).body(Map.of(
                    "verified", false, "error", "No active code. Request a fresh one — codes expire after 10 minutes."));
        } catch (OtpService.TooManyRequestsException e) {
            return ResponseEntity.status(429).body(Map.of(
                    "verified", false, "error", "Too many wrong guesses. Request a fresh code to try again."));
        }
    }

    /**
     * Trust the email of a sign-in that arrived through GitHub or Google. The
     * provider list comes from Firebase Admin, not from anything the client
     * claims — a password user calling this endpoint gains nothing.
     */
    @PostMapping("/oauth-sync")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> oauthSync() {
        FirebaseToken user = currentUser();

        try {
            if (user.getEmail() != null && firebaseUsers.hasVerifiedOAuthProvider(user.getUid())) {
                otpService.markVerified(user.getEmail());
                firebaseUsers.markEmailVerified(user.getUid());
                return ResponseEntity.ok(Map.of("verified", true));
            }
            return ResponseEntity.ok(Map.of(
                    "verified", otpService.isVerified(user.getEmail(), user.isEmailVerified())));
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of(
                    "error", "Could not confirm your sign-in provider. Please try again."));
        }
    }

    /** Does this account still owe us a verified address? */
    @GetMapping("/verification-status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> status() {
        FirebaseToken user = currentUser();
        return ResponseEntity.ok(Map.of(
                "otpEnabled", otpService.isEnabled(),
                "verified", otpService.isVerified(user.getEmail(), user.isEmailVerified())));
    }

    private FirebaseToken currentUser() {
        return (FirebaseToken) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
    }
}
