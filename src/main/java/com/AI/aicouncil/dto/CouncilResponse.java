package com.AI.aicouncil.dto;

public class CouncilResponse {

    private String question;
    private CouncilFlowResponse flow;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public CouncilFlowResponse getFlow() {
        return flow;
    }

    public void setFlow(CouncilFlowResponse flow) {
        this.flow = flow;
    }
}
