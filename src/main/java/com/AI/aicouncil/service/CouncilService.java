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
                    "Hello! I'm Veritas. Ask me anything and I'll gather insights from all my agents.",
                    100,
                    true
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
