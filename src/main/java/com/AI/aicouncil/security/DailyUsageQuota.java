package com.AI.aicouncil.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/**
 * Daily budgets on top of the per-minute {@link UserRateLimiter}.
 *
 * <p>Every council question costs five LLM calls, and the free tiers give the
 * whole site only a few hundred calls a day. The per-minute limiter stops
 * bursts; this stops one account (or a handful of throwaway accounts) from
 * draining the day's quota for everyone.</p>
 *
 * <ul>
 *   <li><b>Per user</b> — at most {@code perUserDaily} questions per UTC day.</li>
 *   <li><b>Global</b> — at most {@code globalDaily} questions per UTC day across
 *       all users, so new accounts can't be farmed to bypass the per-user cap.</li>
 * </ul>
 *
 * <p>Counters reset at 00:00 UTC. They live in memory, so a restart also
 * resets them — acceptable for a single free-tier instance.</p>
 */
@Component
public class DailyUsageQuota {

    public enum Result { ALLOWED, USER_LIMIT, GLOBAL_LIMIT }

    private final int perUserDaily;
    private final int globalDaily;
    private Clock clock = Clock.systemUTC();

    private LocalDate day;
    private final Map<String, Integer> perUser = new HashMap<>();
    private int global;

    public DailyUsageQuota(
            @Value("${rate-limit.per-user-daily:25}") int perUserDaily,
            @Value("${rate-limit.global-daily:200}") int globalDaily) {
        this.perUserDaily = Math.max(1, perUserDaily);
        this.globalDaily = Math.max(1, globalDaily);
    }

    /** Test hook. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    public synchronized Result tryConsume(String key) {
        rollover();
        if (global >= globalDaily) return Result.GLOBAL_LIMIT;
        int used = perUser.getOrDefault(key, 0);
        if (used >= perUserDaily) return Result.USER_LIMIT;
        perUser.put(key, used + 1);
        global++;
        return Result.ALLOWED;
    }

    /** Gives a unit back, e.g. when the request failed or cost no LLM calls. */
    public synchronized void refund(String key) {
        rollover();
        Integer used = perUser.get(key);
        if (used == null || used <= 0) return;
        if (used == 1) perUser.remove(key); else perUser.put(key, used - 1);
        if (global > 0) global--;
    }

    /** Seconds until the counters reset (next 00:00 UTC), at least 1. */
    public long secondsUntilReset() {
        Instant now = clock.instant();
        Instant midnight = LocalDate.ofInstant(now, ZoneOffset.UTC).plusDays(1)
                .atStartOfDay().toInstant(ZoneOffset.UTC);
        return Math.max(1, Duration.between(now, midnight).getSeconds());
    }

    public int perUserDaily() {
        return perUserDaily;
    }

    private void rollover() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (!today.equals(day)) {
            day = today;
            perUser.clear();
            global = 0;
        }
    }
}
