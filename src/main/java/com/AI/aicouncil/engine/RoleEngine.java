package com.AI.aicouncil.engine;

import com.AI.aicouncil.model.CouncilRole;

public interface RoleEngine {

    CouncilRole getRole();

    String generate(String input);
}
