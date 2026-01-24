package com.AI.aicouncil.engine.impl;

import com.AI.aicouncil.ai.SynthesizerAi;
import com.AI.aicouncil.engine.SynthesizerEngine;
import com.AI.aicouncil.model.CouncilMemory;
import com.AI.aicouncil.model.CouncilRole;
import org.springframework.stereotype.Component;

@Component
public class DefaultSynthesizerEngine implements SynthesizerEngine {

    private final SynthesizerAi synthesizerAi;

    public DefaultSynthesizerEngine(SynthesizerAi synthesizerAi) {
        this.synthesizerAi = synthesizerAi;
    }

    @Override
    public String synthesize(String question, CouncilMemory memory) {

        String input = """
        Question:
        %s

        Analyst:
        %s

        Strategist:
        %s

        Critic:
        %s

        Optimizer:
        %s
        """.formatted(
                question,
                memory.get(CouncilRole.ANALYST),
                memory.get(CouncilRole.STRATEGIST),
                memory.get(CouncilRole.CRITIC),
                memory.get(CouncilRole.OPTIMIZER)
        );

        return synthesizerAi.synthesize(input);
    }

}
