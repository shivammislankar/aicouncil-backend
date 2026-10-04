package com.AI.aicouncil.service;

import com.AI.aicouncil.engine.*;
import com.AI.aicouncil.model.*;
import com.AI.aicouncil.security.CurrentUser;
import com.AI.aicouncil.security.PromptSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CouncilService {

    private static final Logger log = LoggerFactory.getLogger(CouncilService.class);

    /** Matches the trailing "CONFIDENCE: NN" line the synthesizer is instructed to append. */
    private static final Pattern CONFIDENCE_LINE = Pattern.compile(
            "(?im)^[ \\t]*CONFIDENCE:[ \\t]*(\\d{1,3})(?:[ \\t]*/[ \\t]*100)?%?[ \\t]*\\.?[ \\t]*$");

    private static final int DEFAULT_CONFIDENCE = 85;

    /** Package-private for unit testing. */
    record Synthesis(String answer, int confidence) {}

    private final AnalystEngine analyst;
    private final StrategistEngine strategist;
    private final CriticEngine critic;
    private final OptimizerEngine optimizer;
    private final SynthesizerEngine synthesizer;
    private final CurrentUser currentUser;

    public CouncilService(
            AnalystEngine analyst,
            StrategistEngine strategist,
            CriticEngine critic,
            OptimizerEngine optimizer,
            SynthesizerEngine synthesizer,
            CurrentUser currentUser
    ) {
        this.analyst = analyst;
        this.strategist = strategist;
        this.critic = critic;
        this.optimizer = optimizer;
        this.synthesizer = synthesizer;
        this.currentUser=currentUser;
    }

    private static final java.util.Set<String> GREETINGS = java.util.Set.of(
            "hi", "hello", "hey", "yo", "sup", "hola",
            "good morning", "good afternoon", "good evening",
            "howdy", "greetings", "what's up", "wassup"
    );

    private boolean isGreeting(String question) {
        if (question == null || question.isBlank()) return false;
        String normalized = question.toLowerCase()
                .trim()
                .replaceAll("[!?.]+$", "");
        return GREETINGS.contains(normalized);
    }

    /**
     * Runs one council stage, retrying once after a short pause.
     * Free models on OpenRouter occasionally return 429 (rate limit) or time
     * out mid-chain; a single retry keeps one transient failure from failing
     * the whole request.
     */
    private String withRetry(String stage, Supplier<String> call) {
        try {
            return call.get();
        } catch (RuntimeException first) {
            log.warn("{} agent call failed: {}. Retrying once after 2s.", stage, first.getMessage());
            try {
                Thread.sleep(2000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw first;
            }
            return call.get(); // second failure propagates to the controller
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
        String uid = currentUser.uid();
        String email = currentUser.email();
        String safeQuestion = PromptSanitizer.sanitize(question);

        // Skip all agents for simple greetings (saves API cost)
        if (isGreeting(safeQuestion)) {
            return new CouncilResponse(
                    question,
                    Map.of(CouncilRole.GREETING, "Hello! How can I help you today?"),
                    "Hello! I'm the AI Council. Ask me anything and I'll gather insights from all my agents.",
                    100
            );
        }

        CouncilMemory memory = new CouncilMemory();

        // 1. Analyst (NO memory yet)
        String analysis = withRetry("Analyst", () -> analyst.analyze(safeQuestion));
        memory.put(CouncilRole.ANALYST, analysis);

        // 2. Strategist
        String strategy = withRetry("Strategist", () -> strategist.strategize(safeQuestion, memory));
        memory.put(CouncilRole.STRATEGIST, strategy);

        // 3. Critic
        String critique = withRetry("Critic", () -> critic.critique(safeQuestion, memory));
        memory.put(CouncilRole.CRITIC, critique);

        // 4. Optimizer
        String optimization = withRetry("Optimizer", () -> optimizer.optimize(safeQuestion, memory));
        memory.put(CouncilRole.OPTIMIZER, optimization);

        // 5. Synthesizer
        String rawSynthesis = withRetry("Synthesizer", () -> synthesizer.synthesize(safeQuestion, memory));
        Synthesis synthesis = parseSynthesis(rawSynthesis);

        return new CouncilResponse(
                question,
                memory.getAll(),
                synthesis.answer(),
                synthesis.confidence()
        );
    }
}
