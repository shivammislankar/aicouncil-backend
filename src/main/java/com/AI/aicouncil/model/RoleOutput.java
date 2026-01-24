package com.AI.aicouncil.model;

public class RoleOutput {

    private CouncilRole role;
    private String content;

    public RoleOutput(CouncilRole role, String content) {
        this.role = role;
        this.content = content;
    }

    public CouncilRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }
}
