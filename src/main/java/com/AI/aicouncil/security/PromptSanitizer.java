package com.AI.aicouncil.security;

public class PromptSanitizer {

    private static final String[] BLOCKED_PATTERNS = {
            "ignore previous",
            "disregard above",
            "system prompt",
            "developer message",
            "act as",
            "you are now",
            "role:",
            "assistant:",
            "change behavior"
    };

    public static String sanitize(String input) {
        String lowered = input.toLowerCase();

        for (String pattern : BLOCKED_PATTERNS) {
            if (lowered.contains(pattern)) {
                return "[UNTRUSTED USER INPUT DETECTED – CONTENT SANITIZED]";
            }
        }
        return input;
    }
}
