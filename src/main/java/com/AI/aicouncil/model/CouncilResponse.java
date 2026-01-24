package com.AI.aicouncil.model;

import java.util.Map;

public class CouncilResponse {

    private String question;
    private Map<CouncilRole, String> flow;
    private String finalAnswer;
    private int confidence;

    public CouncilResponse(
            String question,
            Map<CouncilRole, String> flow,
            String finalAnswer,
            int confidence
    ) {
        this.question = question;
        this.flow = flow;
        this.finalAnswer = finalAnswer;
        this.confidence = confidence;
    }

    public String getQuestion() {
        return question;
    }

    public Map<CouncilRole, String> getFlow() {
        return flow;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public int getConfidence() {
        return confidence;
    }
}
