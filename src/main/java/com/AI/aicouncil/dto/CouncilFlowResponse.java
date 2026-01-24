package com.AI.aicouncil.dto;

public class CouncilFlowResponse {

    private String analyst;
    private String strategist;
    private String critic;
    private String optimizer;
    private SynthesizerResponse synthesizer;

    public String getAnalyst() {
        return analyst;
    }

    public void setAnalyst(String analyst) {
        this.analyst = analyst;
    }

    public String getStrategist() {
        return strategist;
    }

    public void setStrategist(String strategist) {
        this.strategist = strategist;
    }

    public String getCritic() {
        return critic;
    }

    public void setCritic(String critic) {
        this.critic = critic;
    }

    public String getOptimizer() {
        return optimizer;
    }

    public void setOptimizer(String optimizer) {
        this.optimizer = optimizer;
    }

    public SynthesizerResponse getSynthesizer() {
        return synthesizer;
    }

    public void setSynthesizer(SynthesizerResponse synthesizer) {
        this.synthesizer = synthesizer;
    }
}
