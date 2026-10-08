package com.AI.aicouncil.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserRateLimiterTest {

    @Test
    void allowsUpToTheLimitThenBlocks() {
        UserRateLimiter limiter = new UserRateLimiter(60, 3);

        assertTrue(limiter.tryAcquire("user-a"));
        assertTrue(limiter.tryAcquire("user-a"));
        assertTrue(limiter.tryAcquire("user-a"));
        assertFalse(limiter.tryAcquire("user-a"), "4th call in the window must be blocked");
    }

    @Test
    void limitsAreIndependentPerUser() {
        UserRateLimiter limiter = new UserRateLimiter(60, 2);

        assertTrue(limiter.tryAcquire("user-a"));
        assertTrue(limiter.tryAcquire("user-a"));
        assertFalse(limiter.tryAcquire("user-a"));

        // a different user starts with a full budget
        assertTrue(limiter.tryAcquire("user-b"));
        assertTrue(limiter.tryAcquire("user-b"));
        assertFalse(limiter.tryAcquire("user-b"));
    }

    @Test
    void blockedCallerGetsAUsefulRetryAfter() {
        UserRateLimiter limiter = new UserRateLimiter(60, 1);

        assertTrue(limiter.tryAcquire("user-a"));
        assertFalse(limiter.tryAcquire("user-a"));

        long retry = limiter.retryAfterSeconds("user-a");
        assertTrue(retry >= 1 && retry <= 60, "retry-after was " + retry);
        assertEquals(1, limiter.retryAfterSeconds("unknown-user"));
    }

    @Test
    void rejectsNonsenseConfiguration() {
        // zero/negative config is clamped rather than bricking the endpoint
        UserRateLimiter limiter = new UserRateLimiter(0, 0);
        assertTrue(limiter.tryAcquire("user-a"));
        assertEquals(1, limiter.maxPerWindow());
    }
}
