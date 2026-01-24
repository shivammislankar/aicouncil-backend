package com.AI.aicouncil;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class AicouncilApplication {
    public static void main(String[] args) {
        SpringApplication.run(AicouncilApplication.class, args);
    }
}

