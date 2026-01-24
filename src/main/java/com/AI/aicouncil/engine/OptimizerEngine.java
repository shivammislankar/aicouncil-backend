package com.AI.aicouncil.engine;

import com.AI.aicouncil.model.CouncilMemory;

public interface OptimizerEngine {
    String optimize(String question, CouncilMemory memory);
}
