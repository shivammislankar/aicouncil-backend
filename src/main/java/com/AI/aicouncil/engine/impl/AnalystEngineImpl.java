package com.AI.aicouncil.engine.impl;

import com.AI.aicouncil.ai.AnalystAi;
import com.AI.aicouncil.engine.AnalystEngine;
import org.springframework.stereotype.Component;

@Component
public class AnalystEngineImpl implements AnalystEngine {

    private final AnalystAi analystAi;

    public AnalystEngineImpl(AnalystAi analystAi) {
        this.analystAi = analystAi;
    }

    @Override
    public String analyze(String question) {
        return analystAi.analyze(question);
    }
}
