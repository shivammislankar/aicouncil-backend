package com.AI.aicouncil.engine;

import com.AI.aicouncil.model.CouncilMemory;

public interface SynthesizerEngine {
    String synthesize(String question, CouncilMemory memory);
}

