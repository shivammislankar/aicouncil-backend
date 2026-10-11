package com.AI.aicouncil.controller;

import com.AI.aicouncil.security.UserRateLimiter;
import com.AI.aicouncil.service.OtpService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Email OTP verification and OAuth provider sync.
 *
 * <p>{@code POST /api/auth/otp/request} is public (it only needs an address).
 * Everything else here requires a Firebase session, and the email being
 * verified must match the signed-in account — nobody can verify someone
 * else's address with their own login.</p>
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"http://localhost:5173", "https://ai-council.vercel.app", "https://ai-council-one-livid.vercel.app", "https://aicouncil-one-livid.vercel.app"})
public class AuthController {

    private final OtpService otpService;
    private final UserRateLimiter rateLimiter;

    public AuthController(OtpService otpService, UserRateLimiter rateLimiter) {
        this.otpService = otpService;
        this.rateLimiter = rateLimiter;
    }

    public record OtpRequest(@Email @NotBlank String email) {
    }

    public record OtpVerifyRequest(@NotBlank String code) {
    }

    /** Public: email a fresh 6-digit code. Throttled per address. */
    @PostMapping("/otp/request")
    public ResponseEntity<Map<String, Object>> requestCode(@Valid @RequestBody OtpRequest request) {
        if (!otpService.isEnabled()) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "Email verification is not configured yet. Please try again later."));
        }

        String email = OtpService.normalize(request.email());
        if (!rateLimiter.tryAcquire("otp:" + email)) {
            return ResponseEntity.status(429)
                    .header("Retry-After", String.valueOf(rateLimiter.retryAfterSeconds("otp:" + email)))
                    .body(Map.of("error", "Too many code requests. Please wait a moment and try again."));
        }

        try {
            otpService.requestCode(email);
        } catch (OtpService.TooManyRequestsException e) {
            return ResponseEntity.status(429).body(Map.of(
                    "error", "Too many codes requested. New codes are available again in 10 minutes."));
        } catch (Exception e) {
            // Mail failures stay generic: the cause (bad credentials, quota) is
            // ours to fix, not the visitor's to debug.
            return ResponseEntity.status(502).body(Map.of(
                    "error", "Could not send the code right now. Please try again in a minute."));
        }

        // The response never confirms whether the address has an account:
        // that distinction is exactly what account-enumeration attacks want.
        return ResponseEntity.ok(Map.of(
                "message", "If that address can receive mail, a code is on its way."));
    }

    /** Authenticated: check the code for the signed-in account's own address. */
    @PostMapping("/otp/verify")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> verifyCode(@Valid @RequestBody OtpVerifyRequest request) {
        FirebaseToken user = currentUser();
        String email = user.getEmail();

        if (!otpService.isEnabled()) {
            return ResponseEntity.ok(Map.of("verified", true));
        }
        if (email == null) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "Your sign-in has no email address to verify."));
        }

        try {
            boolean ok = otpService.verifyCode(email, request.code());
            if (ok) {
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
     * Authenticated: trust the email of a sign-in that arrived through an
     * already-verified OAuth provider (GitHub). The provider list comes from
     * Firebase Admin, not from anything the client claims — a password user
     * calling this endpoint gains nothing.
     */
    @PostMapping("/oauth-sync")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> oauthSync() {
        FirebaseToken user = currentUser();

        try {
            UserRecord record = FirebaseAuth.getInstance().getUser(user.getUid());
            boolean oauthVerified = java.util.Arrays.stream(record.getProviderData())
                    .anyMatch(p -> "github.com".equals(p.getProviderId())
                            || "google.com".equals(p.getProviderId()));

            if (oauthVerified && user.getEmail() != null) {
                otpService.markVerified(user.getEmail());
                return ResponseEntity.ok(Map.of("verified", true));
            }
            return ResponseEntity.ok(Map.of(
                    "verified", otpService.isVerified(user.getEmail())));
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of(
                    "error", "Could not confirm your sign-in provider. Please try again."));
        }
    }

    /** Authenticated: does this account still owe us a verified address? */
    @GetMapping("/verification-status")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> status() {
        FirebaseToken user = currentUser();
        return ResponseEntity.ok(Map.of(
                "otpEnabled", otpService.isEnabled(),
                "verified", otpService.isVerified(user.getEmail())));
    }

    private FirebaseToken currentUser() {
        return (FirebaseToken) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
    }
}
