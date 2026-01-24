package com.AI.aicouncil.service;

import com.AI.aicouncil.engine.*;
import com.AI.aicouncil.model.*;
import com.AI.aicouncil.security.CurrentUser;
import com.AI.aicouncil.security.PromptSanitizer;
import org.springframework.stereotype.Service;

@Service
public class CouncilService {

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

    public CouncilResponse processQuestion(String question) {
        String uid = currentUser.uid();   // ✅ authenticated user
        String email = currentUser.email();
        String safeQuestion = PromptSanitizer.sanitize(question);

        CouncilMemory memory = new CouncilMemory();

        // 1️⃣ Analyst (NO memory yet)
        String analysis = analyst.analyze(safeQuestion);
        memory.put(CouncilRole.ANALYST, analysis);

        // 2️⃣ Strategist
        String strategy = strategist.strategize(safeQuestion, memory);
        memory.put(CouncilRole.STRATEGIST, strategy);

        // 3️⃣ Critic
        String critique = critic.critique(safeQuestion, memory);
        memory.put(CouncilRole.CRITIC, critique);

        // 4️⃣ Optimizer
        String optimization = optimizer.optimize(safeQuestion, memory);
        memory.put(CouncilRole.OPTIMIZER, optimization);

        // 5️⃣ Synthesizer
        String finalAnswer = synthesizer.synthesize(safeQuestion, memory);

        return new CouncilResponse(
                question,
                memory.getAll(),
                finalAnswer,
                85
        );
    }
}
