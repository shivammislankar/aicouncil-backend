package com.AI.aicouncil.controller;

import com.AI.aicouncil.dto.CouncilRequest;
import com.AI.aicouncil.model.CouncilResponse;
import com.AI.aicouncil.security.UserRateLimiter;
import com.AI.aicouncil.service.CouncilService;
import com.AI.aicouncil.service.OtpService;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
@RestController
@RequestMapping("/api/council")
@CrossOrigin(origins = {"http://localhost:5173", "https://ai-council.vercel.app", "https://ai-council-one-livid.vercel.app", "https://aicouncil-one-livid.vercel.app"})
public class CouncilController {

    private final CouncilService councilService;
    private final UserRateLimiter rateLimiter;
    private final OtpService otpService;

    public CouncilController(CouncilService councilService, UserRateLimiter rateLimiter, OtpService otpService) {
        this.councilService = councilService;
        this.rateLimiter = rateLimiter;
        this.otpService = otpService;
    }

    @PostMapping("/ask")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> ask(@RequestBody CouncilRequest request) {

        FirebaseToken user =
                (FirebaseToken) SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getPrincipal();

        String uid = user.getUid();
        String email = user.getEmail();

        // Free LLM tiers are a shared, finite budget - stop one account from
        // exhausting them for everybody else.
        if (!rateLimiter.tryAcquire(uid)) {
            return ResponseEntity
                    .status(429)
                    .header("Retry-After", String.valueOf(rateLimiter.retryAfterSeconds(uid)))
                    .body(Map.of(
                            "error", "Too many questions in the last minute. Please wait a moment and try again.",
                            "limit", rateLimiter.maxPerWindow()
                    ));
        }

        // Password accounts must prove their address first. OAuth arrivals
        // (GitHub) are marked verified at sign-in, so they sail through.
        if (!otpService.isVerified(email)) {
            return ResponseEntity
                    .status(403)
                    .body(Map.of(
                            "error", "EMAIL_NOT_VERIFIED",
                            "message", "Verify your email address before asking the council."
                    ));
        }

        return ResponseEntity.ok(Map.of(
                "user", email != null ? email : uid,
                "payload", councilService.processQuestion(request.getQuestion())
        ));
    }
    @GetMapping("/admin/health")
    @PreAuthorize("hasRole('ADMIN')")
    public String admin() {
        return "ADMIN OK";
    }

}
