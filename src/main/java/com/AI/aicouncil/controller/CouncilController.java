package com.AI.aicouncil.controller;

import com.AI.aicouncil.dto.CouncilRequest;
import com.AI.aicouncil.model.CouncilResponse;
import com.AI.aicouncil.security.DailyUsageQuota;
import com.AI.aicouncil.security.UserRateLimiter;
import com.AI.aicouncil.service.CouncilService;
import com.AI.aicouncil.service.OtpService;
import com.google.firebase.auth.FirebaseToken;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/council")
public class CouncilController {

    private static final Logger log = LoggerFactory.getLogger(CouncilController.class);

    private final CouncilService councilService;
    private final UserRateLimiter rateLimiter;
    private final DailyUsageQuota dailyQuota;
    private final OtpService otpService;

    public CouncilController(CouncilService councilService,
                             UserRateLimiter rateLimiter,
                             DailyUsageQuota dailyQuota,
                             OtpService otpService) {
        this.councilService = councilService;
        this.rateLimiter = rateLimiter;
        this.dailyQuota = dailyQuota;
        this.otpService = otpService;
    }

    @PostMapping("/ask")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> ask(@Valid @RequestBody CouncilRequest request) {

        FirebaseToken user =
                (FirebaseToken) SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getPrincipal();

        String uid = user.getUid();
        String email = user.getEmail();

        // Check verification first so unverified accounts never spend quota.
        if (!otpService.isVerified(email, user.isEmailVerified())) {
            return ResponseEntity
                    .status(403)
                    .body(Map.of(
                            "error", "EMAIL_NOT_VERIFIED",
                            "message", "Verify your email address before asking the council."
                    ));
        }

        // Burst guard: stops scripts hammering the endpoint.
        if (!rateLimiter.tryAcquire(uid)) {
            return ResponseEntity
                    .status(429)
                    .header("Retry-After", String.valueOf(rateLimiter.retryAfterSeconds(uid)))
                    .body(Map.of(
                            "error", "Too many questions in the last minute. Please wait a moment and try again.",
                            "limit", rateLimiter.maxPerWindow()
                    ));
        }

        // Daily budget: the free LLM tiers are shared by everybody.
        DailyUsageQuota.Result quota = dailyQuota.tryConsume(uid);
        if (quota != DailyUsageQuota.Result.ALLOWED) {
            String message = quota == DailyUsageQuota.Result.USER_LIMIT
                    ? "You've reached today's limit of " + dailyQuota.perUserDaily() + " questions. It resets at 00:00 UTC."
                    : "Veritas has reached its daily capacity on the free model tiers. Please try again tomorrow.";
            return ResponseEntity
                    .status(429)
                    .header("Retry-After", String.valueOf(dailyQuota.secondsUntilReset()))
                    .body(Map.of("error", message));
        }

        CouncilResponse payload;
        try {
            payload = councilService.processQuestion(request.getQuestion().trim());
        } catch (CouncilService.CouncilTimeoutException e) {
            dailyQuota.refund(uid);
            log.warn("Council run exceeded its time budget at stage {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of(
                    "error", "The AI models are busy right now and the council ran out of time. Please try again in a minute."));
        } catch (RuntimeException e) {
            dailyQuota.refund(uid);
            log.warn("Council run failed: {}", e.toString());
            return ResponseEntity.status(503).body(Map.of(
                    "error", "The AI models are unavailable right now. Please try again in a minute."));
        }

        // Greetings never reach the LLMs, so they shouldn't count against the day.
        if (payload.isGreeting()) {
            dailyQuota.refund(uid);
        }

        return ResponseEntity.ok(Map.of(
                "user", email != null ? email : uid,
                "payload", payload
        ));
    }

    @GetMapping("/admin/health")
    @PreAuthorize("hasRole('ADMIN')")
    public String admin() {
        return "ADMIN OK";
    }
}
