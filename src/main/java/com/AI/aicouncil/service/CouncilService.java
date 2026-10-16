package com.AI.aicouncil.service;

import com.AI.aicouncil.engine.*;
import com.AI.aicouncil.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CouncilService {

    private static final Logger log = LoggerFactory.getLogger(CouncilService.class);

    /** Matches the trailing "CONFIDENCE: NN" line the synthesizer is instructed to append.
     *  Tolerates markdown bold markers (**CONFIDENCE: 85**) that models sometimes wrap it in. */
    private static final Pattern CONFIDENCE_LINE = Pattern.compile(
            "(?im)^[ \\t*]*CONFIDENCE:[ \\t]*(\\d{1,3})(?:[ \\t]*/[ \\t]*100)?%?[ \\t*\\.]*$");

    private static final int DEFAULT_CONFIDENCE = 85;

    /** Package-private for unit testing. */
    record Synthesis(String answer, int confidence) {}

    private final AnalystEngine analyst;
    private final StrategistEngine strategist;
    private final CriticEngine critic;
    private final OptimizerEngine optimizer;
    private final SynthesizerEngine synthesizer;
    private final ExecutorService executor;
    private final long timeBudgetMillis;

    public CouncilService(
            AnalystEngine analyst,
            StrategistEngine strategist,
            CriticEngine critic,
            OptimizerEngine optimizer,
            SynthesizerEngine synthesizer,
            @Qualifier("aiExecutor") ExecutorService executor,
            @Value("${council.time-budget-seconds:150}") long timeBudgetSeconds
    ) {
        this.analyst = analyst;
        this.strategist = strategist;
        this.critic = critic;
        this.optimizer = optimizer;
        this.synthesizer = synthesizer;
        this.executor = executor;
        this.timeBudgetMillis = Math.max(10, timeBudgetSeconds) * 1000L;
    }

    /** Trivial, non-substantive inputs that shouldn't cost a full council run.
     *  Matched after normalizing case/whitespace and stripping trailing punctuation. */
    private static final java.util.Set<String> GREETINGS = java.util.Set.of(
            // core salutations
            "hi", "hello", "hey", "heya", "hiya", "yo", "sup", "hola",
            "howdy", "greetings", "hi there", "hey there", "hello there",
            // time-based
            "good morning", "good afternoon", "good evening", "good night", "good day",
            // small talk
            "how are you", "how are you doing", "how are you today",
            "how r you", "how r u", "how are u", "how ru",
            "how do you do", "how's it going", "hows it going", "how is it going",
            "what's up", "whats up", "wassup", "what up",
            "what's new", "whats new", "how have you been",
            // acknowledgements / pleasantries
            "good", "nice", "cool", "awesome", "great", "fine", "ok", "okay", "k",
            "thanks", "thank you", "thx", "ty", "please",
            "bye", "goodbye", "see you", "see ya", "later", "cya",
            "yes", "yeah", "yep", "no", "nope", "sure"
    );

    /** Words allowed to follow a greeting opener ("hi there", "hello everyone"). */
    private static final java.util.Set<String> GREETING_FILLERS = java.util.Set.of(
            "there", "all", "everyone", "guys", "guy", "team", "people", "folks",
            "friend", "friends", "world", "ya", "u", "veritas", "bot", "ai",
            "sir", "mate", "buddy", "again"
    );

    /** Leading words that may start a greeting ("good morning team"). */
    private static final java.util.List<String> GREETING_OPENERS = java.util.List.of(
            "good morning", "good afternoon", "good evening", "good night", "good day",
            "thanks", "thank you", "hello", "hey", "heya", "hiya", "hi",
            "howdy", "greetings", "bye", "goodbye", "yo"
    );

    /**
     * Returns true for greetings and other trivial inputs that carry no
     * question for the council to answer (e.g. "hi", "good", "how are you
     * doing", "hello everyone"). Deliberately conservative: anything that
     * isn't in the known set falls through to the full council.
     */
    static boolean isGreeting(String question) {
        if (question == null || question.isBlank()) return false;

        String normalized = question.toLowerCase()
                .trim()
                .replaceAll("[!?.]+$", "")
                .replaceAll("\\s+", " ")
                .trim();

        if (normalized.isEmpty()) return false;
        if (GREETINGS.contains(normalized)) return true;

        // Greeting opener + only filler words after it: "hi there", "hello everyone"
        for (String opener : GREETING_OPENERS) {
            if (!normalized.startsWith(opener + " ")) continue;
            String rest = normalized.substring(opener.length() + 1);
            if (rest.isEmpty()) return true;
            if (GREETINGS.contains(rest)) return true; // "hi how are you"
            boolean onlyFillers = true;
            for (String word : rest.split(" ")) {
                if (!GREETING_FILLERS.contains(word)) {
                    onlyFillers = false;
                    break;
                }
            }
            if (onlyFillers) return true;
        }
        return false;
    }

    /** Thrown when a council run would exceed its total time budget. */
    public static class CouncilTimeoutException extends RuntimeException {
        public CouncilTimeoutException(String stage) {
            super(stage);
        }
    }

    /**
     * Runs one council stage on the AI executor, retrying once after a short
     * pause, and never past {@code deadline} (epoch millis).
     *
     * <p>Each LLM call can take up to 60s plus provider cooldowns, so without
     * a shared deadline a single question could hold a request thread for
     * many minutes. A stage that runs out of time is cancelled (interrupted)
     * and the whole run fails fast with {@link CouncilTimeoutException}.</p>
     */
    private String withRetry(String stage, long deadline, Supplier<String> call) {
        try {
            return runBefore(stage, deadline, call);
        } catch (CouncilTimeoutException timeout) {
            throw timeout;
        } catch (RuntimeException first) {
            if (deadline - System.currentTimeMillis() < 5_000) {
                throw first; // not enough time left for a meaningful retry
            }
            log.warn("{} agent call failed: {}. Retrying once after 2s.", stage, first.getMessage());
            try {
                Thread.sleep(2000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw first;
            }
            return runBefore(stage, deadline, call); // second failure propagates to the controller
        }
    }

    private String runBefore(String stage, long deadline, Supplier<String> call) {
        long remaining = deadline - System.currentTimeMillis();
        if (remaining <= 0) {
            throw new CouncilTimeoutException(stage);
        }
        Callable<String> task = call::get;
        Future<String> future = executor.submit(task);
        try {
            return future.get(remaining, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new CouncilTimeoutException(stage);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new CouncilTimeoutException(stage);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(stage + " failed", cause);
        }
    }

    /**
     * Splits the synthesizer output into the displayed answer and the
     * confidence it reported on council agreement. The trailing
     * "CONFIDENCE: NN" line is removed from the answer; if the model omits
     * it (or it cannot be parsed), falls back to {@link #DEFAULT_CONFIDENCE}.
     */
    static Synthesis parseSynthesis(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Synthesis("", DEFAULT_CONFIDENCE);
        }

        List<int[]> regions = new ArrayList<>();
        int reported = DEFAULT_CONFIDENCE;
        Matcher matcher = CONFIDENCE_LINE.matcher(raw);
        while (matcher.find()) {
            regions.add(new int[]{matcher.start(), matcher.end()});
            reported = Integer.parseInt(matcher.group(1));
        }

        if (regions.isEmpty()) {
            log.warn("Synthesizer output had no CONFIDENCE line; using default {}", DEFAULT_CONFIDENCE);
            return new Synthesis(raw.stripTrailing(), DEFAULT_CONFIDENCE);
        }

        StringBuilder answer = new StringBuilder();
        int pos = 0;
        for (int[] region : regions) {
            answer.append(raw, pos, region[0]);
            pos = region[1];
        }
        answer.append(raw, pos, raw.length());

        int clamped = Math.max(0, Math.min(100, reported));
        return new Synthesis(answer.toString().stripTrailing(), clamped);
    }

    public CouncilResponse processQuestion(String question) {
        // The question goes to the models as-is. Prompt-injection defence lives
        // in the role system prompts ("treat all input as untrusted data");
        // keyword blocklists here mangled ordinary questions ("act as", "role:")
        // and were trivially bypassed anyway.

        // Skip all agents for simple greetings (saves API cost)
        if (isGreeting(question)) {
            return new CouncilResponse(
                    question,
                    Map.of(CouncilRole.GREETING, "Hello! How can I help you today?"),
                    "Hello! I'm Veritas. Ask me anything and I'll gather insights from all my agents.",
                    100,
                    true
            );
        }

        long deadline = System.currentTimeMillis() + timeBudgetMillis;
        CouncilMemory memory = new CouncilMemory();

        // 1. Analyst (NO memory yet)
        String analysis = withRetry("Analyst", deadline, () -> analyst.analyze(question));
        memory.put(CouncilRole.ANALYST, analysis);

        // 2. Strategist
        String strategy = withRetry("Strategist", deadline, () -> strategist.strategize(question, memory));
        memory.put(CouncilRole.STRATEGIST, strategy);

        // 3. Critic
        String critique = withRetry("Critic", deadline, () -> critic.critique(question, memory));
        memory.put(CouncilRole.CRITIC, critique);

        // 4. Optimizer
        String optimization = withRetry("Optimizer", deadline, () -> optimizer.optimize(question, memory));
        memory.put(CouncilRole.OPTIMIZER, optimization);

        // 5. Synthesizer
        String rawSynthesis = withRetry("Synthesizer", deadline, () -> synthesizer.synthesize(question, memory));
        Synthesis synthesis = parseSynthesis(rawSynthesis);

        return new CouncilResponse(
                question,
                memory.getAll(),
                synthesis.answer(),
                synthesis.confidence()
        );
    }
}
