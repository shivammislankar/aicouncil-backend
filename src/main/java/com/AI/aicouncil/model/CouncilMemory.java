package com.AI.aicouncil.model;

import java.util.EnumMap;
import java.util.Map;

public class CouncilMemory {

    private final Map<CouncilRole, String> memory =
            new EnumMap<>(CouncilRole.class);

    public void put(CouncilRole role, String output) {
        memory.put(role, output);
    }

    public String get(CouncilRole role) {
        return memory.get(role);
    }

    public Map<CouncilRole, String> getAll() {
        return memory;
    }
}
