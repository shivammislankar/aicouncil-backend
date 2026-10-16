package com.AI.aicouncil.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

    /**
     * Runs council LLM stages so they can be cancelled when a run exceeds its
     * time budget. Four threads matches the free-tier box: extra questions
     * wait their turn (and their wait counts against their own budget).
     */
    @Bean(name = "aiExecutor", destroyMethod = "shutdownNow")
    public ExecutorService aiExecutor() {
        return Executors.newFixedThreadPool(4);
    }
}
