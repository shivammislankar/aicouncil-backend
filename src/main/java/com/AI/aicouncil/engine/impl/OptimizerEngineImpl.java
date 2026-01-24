package com.AI.aicouncil.engine.impl;

import com.AI.aicouncil.ai.OptimizerAi;
import com.AI.aicouncil.engine.OptimizerEngine;
import com.AI.aicouncil.model.CouncilMemory;
import com.AI.aicouncil.model.CouncilRole;
import org.springframework.stereotype.Component;

@Component
public class OptimizerEngineImpl implements OptimizerEngine {

    private final OptimizerAi optimizerAi;

    public OptimizerEngineImpl(OptimizerAi optimizerAi) {
        this.optimizerAi = optimizerAi;
    }

    @Override
    public String optimize(String question, CouncilMemory memory) {

        String input = """
    Question:
    %s

    Analyst:
    %s

    Strategist:
    %s

    Critic:
    %s
    """.formatted(
                question,
                memory.get(CouncilRole.ANALYST),
                memory.get(CouncilRole.STRATEGIST),
                memory.get(CouncilRole.CRITIC)
        );

        return optimizerAi.optimize(input);
    }

}
