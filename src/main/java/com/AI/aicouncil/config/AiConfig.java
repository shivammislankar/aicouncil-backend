package com.AI.aicouncil.config;

import com.AI.aicouncil.ai.*;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AiConfig {

    @Value("${openrouter.api-key}")
    private String apiKey;

    @Value("${openrouter.base-url}")
    private String baseUrl;

    @Value("${openrouter.model}")
    private String model;

    private OpenAiChatModel openRouterModel() {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(model)
                .temperature(0.3)
                // Cap generation: agents are meant to be concise, and output tokens
                // dominate latency on free models (~8s for ~500 tokens).
                .maxTokens(700)
                // Fail fast instead of hanging a single council stage indefinitely.
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    @Bean
    public AnalystAi analystAi() {
        return AiServices.create(AnalystAi.class, openRouterModel());
    }

    @Bean
    public StrategistAi strategistAi() {
        return AiServices.create(StrategistAi.class, openRouterModel());
    }

    @Bean
    public CriticAi criticAi() {
        return AiServices.create(CriticAi.class, openRouterModel());
    }

    @Bean
    public OptimizerAi optimizerAi() {
        return AiServices.create(OptimizerAi.class, openRouterModel());
    }

    @Bean
    public SynthesizerAi synthesizerAi() {
        return AiServices.create(SynthesizerAi.class, openRouterModel());
    }
}
