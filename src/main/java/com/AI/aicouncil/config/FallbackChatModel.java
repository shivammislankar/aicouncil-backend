package com.AI.aicouncil.config;

import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ordered provider chain for every council LLM call: several Gemini free-tier
 * models first (only ~20 requests/day each — see application.yaml), Groq last.
 *
 * <p>Two mechanisms keep free-tier limits transparent to callers:</p>
 * <ol>
 *   <li><b>Quota-aware cooling</b> — when a provider answers 429 with a
 *       "retry in …" delay, its epoch deadline is remembered and the provider
 *       is skipped (no HTTP call) until then, instead of burning seconds and
 *       quota on a model that is dead for hours.</li>
 *   <li><b>Cooldown round</b> — if every provider failed or is cooling, one
 *       pause (bounded, aimed at the soonest deadline) and a second walk of
 *       the chain turn transient double-outages into a success instead of a
 *       500.</li>
 * </ol>
 *
 * <p>Delegating through {@code chat(...)} instead of {@code doChat(...)} lets
 * each provider merge its own default request parameters (model name,
 * temperature, ...) — so a later chain entry never receives an earlier
 * provider's model name. No per-request state is kept on the instance: the
 * same model object serves all concurrent council calls.</p>
 */
public class FallbackChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(FallbackChatModel.class);

    /** Minimum pause between failover rounds. */
    private static final long MIN_COOLDOWN_MILLIS = 12_000;
    /** Cap so a long provider cooldown can never stall a council stage indefinitely. */
    private static final long MAX_COOLDOWN_MILLIS = 35_000;

    /**
     * Parses the retry delay free providers embed in 429 bodies:
     * Gemini {@code "Please retry in 3h6m37.998915656s"},
     * Groq {@code "Please try again in 14.3875s"}.
     */
    private static final Pattern RETRY_IN = Pattern.compile(
            "(?i)(?:retry|try)(?:\\s+\\w+){0,2}\\s+in\\s+(?:(\\d+)h)?(?:(\\d+)m)?([\\d.]+)s");

    /** Provider name → epoch millis until which it is skipped after a rate limit. */
    private static final ConcurrentHashMap<String, Long> COOLING_UNTIL = new ConcurrentHashMap<>();

    /** One entry per upstream model, tried strictly in order. */
    public record Provider(String name, ChatModel model) {}

    private final List<Provider> providers;

    public FallbackChatModel(List<Provider> providers) {
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("At least one LLM provider is required");
        }
        this.providers = List.copyOf(providers);
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        RuntimeException lastFailure = null;
        for (int round = 1; round <= 2; round++) {
            if (round == 2 && !coolDownBeforeSecondRound()) {
                throw lastFailure;
            }
            for (Provider provider : providers) {
                if (isCooling(provider)) {
                    continue;
                }
                try {
                    return provider.model().chat(request);
                } catch (RuntimeException failure) {
                    lastFailure = failure;
                    Long until = rateLimitedUntil(failure);
                    if (until != null) {
                        COOLING_UNTIL.put(provider.name(), until);
                        log.warn("Provider [{}] rate-limited — skipping it until {}",
                                provider.name(), Instant.ofEpochMilli(until));
                    } else {
                        log.warn("Provider [{}] failed ({}: {}) — trying next provider",
                                provider.name(), failure.getClass().getSimpleName(),
                                abbreviate(failure.getMessage()));
                    }
                }
            }
        }
        if (lastFailure == null) {
            lastFailure = new IllegalStateException(
                    "All LLM providers are cooling down after rate limits");
        }
        throw lastFailure;
    }

    private boolean isCooling(Provider provider) {
        Long until = COOLING_UNTIL.get(provider.name());
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            COOLING_UNTIL.remove(provider.name(), until);
            return false;
        }
        return true;
    }

    /**
     * Pauses between failover rounds: the soonest provider unblock time,
     * clamped to [{@link #MIN_COOLDOWN_MILLIS}, {@link #MAX_COOLDOWN_MILLIS}].
     * Groq TPM blocks of ~15-30s fit inside the window; hour-long Gemini
     * quota blocks are capped so the stage can still try the live provider.
     *
     * @return {@code false} if the wait was interrupted
     */
    private boolean coolDownBeforeSecondRound() {
        long now = System.currentTimeMillis();
        long soonest = Long.MAX_VALUE;
        for (Provider provider : providers) {
            Long until = COOLING_UNTIL.get(provider.name());
            if (until != null && until > now) {
                soonest = Math.min(soonest, until);
            }
        }
        long wait = (soonest == Long.MAX_VALUE) ? MIN_COOLDOWN_MILLIS : soonest - now;
        wait = Math.max(MIN_COOLDOWN_MILLIS, Math.min(MAX_COOLDOWN_MILLIS, wait));
        try {
            Thread.sleep(wait);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * @return epoch millis until which this provider should be skipped, or
     *         {@code null} for non-rate-limit failures (try the next one now).
     */
    private static Long rateLimitedUntil(RuntimeException failure) {
        if (!(failure instanceof RateLimitException) || failure.getMessage() == null) {
            return null;
        }
        Matcher matcher = RETRY_IN.matcher(failure.getMessage());
        if (!matcher.find()) {
            return null;
        }
        long seconds = 0;
        if (matcher.group(1) != null) {
            seconds += Long.parseLong(matcher.group(1)) * 3600;
        }
        if (matcher.group(2) != null) {
            seconds += Long.parseLong(matcher.group(2)) * 60;
        }
        seconds += (long) Double.parseDouble(matcher.group(3));
        if (seconds <= 0) {
            return null;
        }
        return System.currentTimeMillis() + seconds * 1000;
    }

    private static String abbreviate(String message) {
        if (message == null) {
            return "(no message)";
        }
        String singleLine = message.replaceAll("\\s+", " ").trim();
        return singleLine.length() > 220 ? singleLine.substring(0, 220) + "…" : singleLine;
    }
}
