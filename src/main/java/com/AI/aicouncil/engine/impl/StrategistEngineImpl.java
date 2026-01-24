package com.AI.aicouncil.engine.impl;

import com.AI.aicouncil.ai.StrategistAi;
import com.AI.aicouncil.engine.StrategistEngine;
import com.AI.aicouncil.model.CouncilMemory;
import com.AI.aicouncil.model.CouncilRole;
import org.springframework.stereotype.Component;

@Component
public class StrategistEngineImpl implements StrategistEngine {

    private final StrategistAi strategistAi;

    public StrategistEngineImpl(StrategistAi strategistAi) {
        this.strategistAi = strategistAi;
    }

    @Override
    public String strategize(String question, CouncilMemory memory) {

        String input = """
        Question:
        %s

        Analyst:
        %s
        """.formatted(
                question,
                memory.get(CouncilRole.ANALYST)
        );

        return strategistAi.strategize(input);
    }

}
