package com.AI.aicouncil.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CouncilRequest {

    @NotBlank(message = "Question cannot be empty")
    @Size(max = 2000, message = "Question must be less than 2000 characters")
    private String question;

    private String identity;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }


    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }
}
