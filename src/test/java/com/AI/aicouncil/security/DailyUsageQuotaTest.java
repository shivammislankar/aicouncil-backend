package com.AI.aicouncil.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyUsageQuotaTest {

    private static Clock at(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    @Test
    void capsEachUserPerDay() {
        DailyUsageQuota quota = new DailyUsageQuota(2, 100);
        quota.setClock(at("2026-10-16T10:00:00Z"));

        assertEquals(DailyUsageQuota.Result.ALLOWED, quota.tryConsume("a"));
        assertEquals(DailyUsageQuota.Result.ALLOWED, quota.tryConsume("a"));
        assertEquals(DailyUsageQuota.Result.USER_LIMIT, quota.tryConsume("a"));
        assertEquals(DailyUsageQuota.Result.ALLOWED, quota.tryConsume("b"), "other users keep their budget");
    }

    @Test
    void globalCapStopsAccountFarming() {
        DailyUsageQuota quota = new DailyUsageQuota(5, 3);
        quota.setClock(at("2026-10-16T10:00:00Z"));

        assertEquals(DailyUsageQuota.Result.ALLOWED, quota.tryConsume("u1"));
        assertEquals(DailyUsageQuota.Result.ALLOWED, quota.tryConsume("u2"));
        assertEquals(DailyUsageQuota.Result.ALLOWED, quota.tryConsume("u3"));
        assertEquals(DailyUsageQuota.Result.GLOBAL_LIMIT, quota.tryConsume("u4"));
    }

    @Test
    void resetsAtUtcMidnight() {
        DailyUsageQuota quota = new DailyUsageQuota(1, 100);
        quota.setClock(at("2026-10-16T23:59:00Z"));
        assertEquals(DailyUsageQuota.Result.ALLOWED, quota.tryConsume("a"));
        assertEquals(DailyUsageQuota.Result.USER_LIMIT, quota.tryConsume("a"));
        assertEquals(60, quota.secondsUntilReset());

        quota.setClock(at("2026-10-17T00:00:01Z"));
        assertEquals(DailyUsageQuota.Result.ALLOWED, quota.tryConsume("a"));
    }

    @Test
    void refundGivesTheUnitBack() {
        DailyUsageQuota quota = new DailyUsageQuota(1, 1);
        quota.setClock(at("2026-10-16T10:00:00Z"));

        assertEquals(DailyUsageQuota.Result.ALLOWED, quota.tryConsume("a"));
        quota.refund("a");
        assertEquals(DailyUsageQuota.Result.ALLOWED, quota.tryConsume("a"));

        quota.refund("never-used"); // no-op, must not go negative
        assertTrue(quota.secondsUntilReset() >= 1);
    }
}
