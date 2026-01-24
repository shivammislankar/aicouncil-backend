package com.AI.aicouncil.model;

public class SynthesizerResult {

    private final String conclusion;
    private final int confidence;

    public SynthesizerResult(String conclusion, int confidence) {
        this.conclusion = conclusion;
        this.confidence = confidence;
    }

    public String getConclusion() {
        return conclusion;
    }

    public int getConfidence() {
        return confidence;
    }
}
