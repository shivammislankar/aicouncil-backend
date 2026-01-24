package com.AI.aicouncil.engine;

import com.AI.aicouncil.model.CouncilMemory;

public interface StrategistEngine {
    String strategize(String question, CouncilMemory memory);
}
