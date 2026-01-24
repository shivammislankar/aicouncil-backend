package com.AI.aicouncil.engine;

import com.AI.aicouncil.model.CouncilMemory;

public interface CriticEngine {
    String critique(String question, CouncilMemory memory);
}
