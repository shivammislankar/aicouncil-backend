package com.AI.aicouncil.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sliding-window, per-user rate limit for {@code POST /api/council/ask}.
 *
 * <p>Veritas runs on free LLM tiers, so a single caller hammering the endpoint
 * can burn the whole day's Gemini/Groq quota for everyone. This is the guard
 * against that: a generous ceiling for humans, a hard stop for scripts.</p>
 *
 * <p>In-memory and therefore per-instance, which is exactly right for a single
 * Render instance — no external dependency, resets on redeploy.</p>
 */
@Component
public class UserRateLimiter {

    private final long windowMillis;
    private final int maxPerWindow;

    /** uid -> timestamps of recent hits, oldest first. */
    private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();

    public UserRateLimiter(
            @Value("${rate-limit.window-seconds:60}") long windowSeconds,
            @Value("${rate-limit.max-per-window:15}") int maxPerWindow) {
        this.windowMillis = Math.max(1, windowSeconds) * 1000L;
        this.maxPerWindow = Math.max(1, maxPerWindow);
    }

    /**
     * Records a hit if the caller is under the limit.
     *
     * @return true when the request is allowed, false when it should be rejected
     */
    public synchronized boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();

        Deque<Long> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        long cutoff = now - windowMillis;
        while (!deque.isEmpty() && deque.peekFirst() <= cutoff) {
            deque.pollFirst();
        }

        if (deque.size() >= maxPerWindow) {
            return false;
        }

        deque.addLast(now);
        sweepOlderThan(cutoff);
        return true;
    }

    /**
     * How long (in whole seconds, at least 1) the caller should wait before their
     * oldest hit ages out of the window. Used for the Retry-After header.
     */
    public synchronized long retryAfterSeconds(String key) {
        Deque<Long> deque = hits.get(key);
        if (deque == null || deque.isEmpty()) return 1;

        long oldest = deque.peekFirst();
        long millis = Math.max(0, (oldest + windowMillis) - System.currentTimeMillis());
        return Math.max(1, (millis + 999) / 1000);
    }

    public int maxPerWindow() {
        return maxPerWindow;
    }

    /**
     * Drops entries with no recent activity so the map can't grow without bound
     * as one-off visitors come and go. Runs on roughly every 100th call.
     */
    private void sweepOlderThan(long cutoff) {
        if (calls.incrementAndGet() % 100 != 0) return;
        hits.entrySet().removeIf(entry -> {
            Deque<Long> deque = entry.getValue();
            return deque == null || deque.isEmpty() || deque.peekLast() < cutoff;
        });
    }
}
