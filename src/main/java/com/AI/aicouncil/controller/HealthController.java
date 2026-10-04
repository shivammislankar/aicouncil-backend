package com.AI.aicouncil.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public liveness endpoint for the hosting platform's health checks
 * (Render pings this path to decide if the instance is up).
 * Deliberately outside /api/council/** so it needs no authentication.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "up");
    }
}
