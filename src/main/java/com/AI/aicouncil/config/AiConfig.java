package com.AI.aicouncil.config;

import com.AI.aicouncil.ai.*;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    private OllamaChatModel ollamaModel() {
        return OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("llama3.2:3b")
                .temperature(0.3)
                .build();
    }

    @Bean
    public AnalystAi analystAi() {
        return AiServices.create(AnalystAi.class, ollamaModel());
    }

    @Bean
    public StrategistAi strategistAi() {
        return AiServices.create(StrategistAi.class, ollamaModel());
    }

    @Bean
    public CriticAi criticAi() {
        return AiServices.create(CriticAi.class, ollamaModel());
    }

    @Bean
    public OptimizerAi optimizerAi() {
        return AiServices.create(OptimizerAi.class, ollamaModel());
    }

    @Bean
    public SynthesizerAi synthesizerAi() {
        return AiServices.create(SynthesizerAi.class, ollamaModel());
    }
}
