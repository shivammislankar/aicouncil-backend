package com.AI.aicouncil.service;

import com.AI.aicouncil.model.EmailVerification;
import com.AI.aicouncil.repository.EmailVerificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Covers the OTP lifecycle without sending real mail or touching a database:
 * issuance, successful verification, wrong guesses, expiry, guess budget,
 * resend budget, and the disabled-mode passthrough.
 */
@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private EmailVerificationRepository repository;

    @Mock
    private JavaMailSender mailSender;

    private final Map<String, EmailVerification> store = new HashMap<>();
    private OtpService service;

    @BeforeEach
    void setUp() {
        store.clear();
        // Lenient: not every test touches both repository methods, and the
        // shared stubs must not fail the ones that don't.
        lenient().when(repository.findByEmail(any())).thenAnswer(inv -> Optional.ofNullable(
                store.get((String) inv.getArgument(0))));
        lenient().when(repository.save(any())).thenAnswer(inv -> {
            EmailVerification row = inv.getArgument(0);
            store.put(row.getEmail(), row);
            return row;
        });
        service = new OtpService(repository, mailSender,
                true, 600, 5, "noreply@example.com", 3, 600);
    }

    @Test
    void requestSendsMailAndStoresOnlyTheHash() {
        service.requestCode("User@Example.com");

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        assertEquals("User@Example.com".toLowerCase(),
                mail.getValue().getTo()[0]);

        EmailVerification row = store.get("user@example.com");
        assertNotNull(row);
        assertNotNull(row.getCodeHash());
        assertEquals(64, row.getCodeHash().length(), "SHA-256 hex is 64 chars");
        assertNotEquals("123456", row.getCodeHash(), "plaintext must never be stored");
        assertNotNull(row.getCodeExpiresAt());
        assertTrue(row.getCodeExpiresAt().isAfter(Instant.now()));
        assertFalse(row.isVerified());
    }

    @Test
    void resendBudgetIsEnforcedPerWindow() {
        service.requestCode("a@example.com");
        service.requestCode("a@example.com");
        service.requestCode("a@example.com");
        assertThrows(OtpService.TooManyRequestsException.class,
                () -> service.requestCode("a@example.com"));
    }

    @Test
    void verifyMarksAddressVerified() {
        service.requestCode("b@example.com");

        // The code itself travels by email; the test recovers it by re-hashing
        // every candidate — exactly what an attacker without the inbox cannot do.
        String recovered = recoverCode("b@example.com");
        assertTrue(service.verifyCode("b@example.com", recovered));
        assertTrue(service.isVerified("b@example.com"));
        // Idempotent: verifying again stays true.
        assertTrue(service.verifyCode("b@example.com", "000000"));
    }

    @Test
    void wrongGuessesDoNotVerifyAndCountAttempts() {
        service.requestCode("c@example.com");

        assertFalse(service.verifyCode("c@example.com", "000000"));
        assertFalse(service.isVerified("c@example.com"));
        assertEquals(1, store.get("c@example.com").getAttempts());
    }

    @Test
    void guessBudgetBurnsTheCode() {
        service.requestCode("d@example.com");

        for (int i = 0; i < 5; i++) {
            assertFalse(service.verifyCode("d@example.com", "000000"));
        }
        // 6th attempt: budget exhausted, code burned.
        assertThrows(OtpService.TooManyRequestsException.class,
                () -> service.verifyCode("d@example.com", "000000"));
        // Even a fresh guess finds no live code after the burn.
        assertThrows(OtpService.NoActiveCodeException.class,
                () -> service.verifyCode("d@example.com", "123456"));
    }

    @Test
    void expiredCodeIsRejected() {
        service.requestCode("e@example.com");
        store.get("e@example.com").setCodeExpiresAt(Instant.now().minusSeconds(1));

        assertThrows(OtpService.NoActiveCodeException.class,
                () -> service.verifyCode("e@example.com", recoverCode("e@example.com")));
    }

    @Test
    void verifyWithoutAnyCodeIsRejected() {
        assertThrows(OtpService.NoActiveCodeException.class,
                () -> service.verifyCode("nobody@example.com", "123456"));
    }

    @Test
    void disabledModePassesEverythingThrough() {
        OtpService off = new OtpService(repository, mailSender,
                false, 600, 5, "", 3, 600);

        assertTrue(off.isVerified("anyone@example.com"));
        assertThrows(OtpService.OtpNotConfiguredException.class,
                () -> off.requestCode("anyone@example.com"));
        assertThrows(OtpService.OtpNotConfiguredException.class,
                () -> off.verifyCode("anyone@example.com", "123456"));
    }

    @Test
    void hashesAreComparedInConstantTime() {
        String h = OtpService.sha256Hex("123456");
        assertTrue(OtpService.constantTimeEquals(h, OtpService.sha256Hex("123456")));
        assertFalse(OtpService.constantTimeEquals(h, OtpService.sha256Hex("123457")));
        assertFalse(OtpService.constantTimeEquals(h, "short"));
        assertFalse(OtpService.constantTimeEquals(null, h));
    }

    /** Brute-forces the 6-digit space against the stored hash (test-only). */
    private String recoverCode(String email) {
        String hash = store.get(email).getCodeHash();
        for (int i = 0; i < 1_000_000; i++) {
            String candidate = String.format("%06d", i);
            if (OtpService.constantTimeEquals(hash, OtpService.sha256Hex(candidate))) {
                return candidate;
            }
        }
        throw new IllegalStateException("code not recoverable");
    }

    @Test
    void firebaseVerifiedFlagCountsEvenWithoutALocalRow() {
        // Simulates a redeploy that wiped the local database.
        assertTrue(service.isVerified("wiped@example.com", true));
        assertFalse(service.isVerified("wiped@example.com", false));
    }
}
