package com.AI.aicouncil.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

    @Bean
    public Executor aiExecutor() {
        return Executors.newFixedThreadPool(4);
    }
}
