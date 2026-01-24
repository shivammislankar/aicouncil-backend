package com.AI.aicouncil.engine.impl;

import com.AI.aicouncil.ai.CriticAi;
import com.AI.aicouncil.engine.CriticEngine;
import com.AI.aicouncil.model.CouncilMemory;
import com.AI.aicouncil.model.CouncilRole;
import org.springframework.stereotype.Component;
@Component
public class CriticEngineImpl implements CriticEngine {

    private final CriticAi criticAi;

    public CriticEngineImpl(CriticAi criticAi) {
        this.criticAi = criticAi;
    }

    @Override
    public String critique(String question, CouncilMemory memory) {

        String input = """
        Question:
        %s

        Analyst Output:
        %s

        Strategist Output:
        %s
        """.formatted(
                question,
                memory.get(CouncilRole.ANALYST),
                memory.get(CouncilRole.STRATEGIST)
        );

        return criticAi.critique(input);
    }
}
