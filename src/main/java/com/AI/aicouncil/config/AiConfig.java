package com.AI.aicouncil.config;

import com.AI.aicouncil.ai.*;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the council's LLM chain: all configured Gemini free-tier models in
 * order (only ~20 requests/day each — {@link FallbackChatModel} skips any
 * that are rate-limited), then Groq as the final fallback. Both are
 * OpenAI-compatible endpoints on free tiers; no card required.
 */
@Configuration
public class AiConfig {

    @Value("${llm.gemini.base-url}")
    private String geminiBaseUrl;

    @Value("${llm.gemini.api-key}")
    private String geminiApiKey;

    @Value("${llm.gemini.models}")
    private List<String> geminiModels;

    @Value("${llm.groq.base-url}")
    private String groqBaseUrl;

    @Value("${llm.groq.api-key}")
    private String groqApiKey;

    @Value("${llm.groq.model}")
    private String groqModel;

    private OpenAiChatModel openAiModel(String baseUrl, String apiKey, String modelName) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.3)
                // Cap generation: agents are meant to be concise, but reasoning
                // models spend part of the budget thinking before answering —
                // 2000 leaves room for both without runaway generations.
                .maxTokens(2000)
                // Fail fast instead of hanging a single council stage indefinitely.
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    private ChatModel councilModel() {
        List<FallbackChatModel.Provider> chain = new ArrayList<>();
        for (String model : geminiModels) {
            chain.add(new FallbackChatModel.Provider("gemini:" + model,
                    openAiModel(geminiBaseUrl, geminiApiKey, model)));
        }
        chain.add(new FallbackChatModel.Provider("groq:" + groqModel,
                openAiModel(groqBaseUrl, groqApiKey, groqModel)));
        return new FallbackChatModel(chain);
    }

    @Bean
    public AnalystAi analystAi() {
        return AiServices.create(AnalystAi.class, councilModel());
    }

    @Bean
    public StrategistAi strategistAi() {
        return AiServices.create(StrategistAi.class, councilModel());
    }

    @Bean
    public CriticAi criticAi() {
        return AiServices.create(CriticAi.class, councilModel());
    }

    @Bean
    public OptimizerAi optimizerAi() {
        return AiServices.create(OptimizerAi.class, councilModel());
    }

    @Bean
    public SynthesizerAi synthesizerAi() {
        return AiServices.create(SynthesizerAi.class, councilModel());
    }
}
