package com.AI.aicouncil.model;

import java.util.Map;

public class CouncilResponse {

    private String question;
    private Map<CouncilRole, String> flow;
    private String finalAnswer;
    private int confidence;
    /** True when the input was a greeting/trivial message, so clients can
     *  skip persisting it to chat history. */
    private boolean greeting;

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
        this.greeting = false;
    }

    public CouncilResponse(
            String question,
            Map<CouncilRole, String> flow,
            String finalAnswer,
            int confidence,
            boolean greeting
    ) {
        this.question = question;
        this.flow = flow;
        this.finalAnswer = finalAnswer;
        this.confidence = confidence;
        this.greeting = greeting;
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

    public boolean isGreeting() {
        return greeting;
    }
}
