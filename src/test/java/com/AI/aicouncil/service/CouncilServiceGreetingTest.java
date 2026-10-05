package com.AI.aicouncil.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the greeting / trivial-input short-circuit that skips all five
 *  agents (and therefore all LLM API calls). */
class CouncilServiceGreetingTest {

    private static final String[] GREETINGS = {
            "hi", "HI", "Hi!", "hello", "Hey.", "yo", "sup", "hola",
            "good morning", "Good Morning!", "good evening", "good night",
            "howdy", "greetings", "what's up", "wassup",
            // newly covered
            "good", "how are you", "how are you doing", "how do you do",
            "how's it going", "hows it going", "what's new",
            "ok", "okay", "thanks", "thank you", "thx", "bye", "goodbye",
            "yes", "no", "cool", "nice", "please", "see you",
            // opener + filler
            "hi there", "hello everyone", "hey guys", "good morning team",
            "hi how are you",
            // whitespace / punctuation noise
            "   hi   ", "hi???"
    };

    @Test
    void matchesGreetingsAndTrivialInputs() {
        for (String input : GREETINGS) {
            assertTrue(CouncilService.isGreeting(input),
                    "expected greeting: '" + input + "'");
        }
    }

    @Test
    void doesNotMatchRealQuestions() {
        String[] questions = {
                "Should a startup focus on product quality or speed to market?",
                "how are you doing this quarter with the revenue targets?", // looks like small talk but carries a real question
                "good books to learn systems design?",
                "what is the capital of France?",
                "no, I need a comparison of PostgreSQL vs MongoDB",
                "thanks, but can you explain recursion?",
                "hi, can you review my resume?", // greeting prefix + real ask
                "Summarize the risks in this business plan."
        };
        for (String input : questions) {
            assertFalse(CouncilService.isGreeting(input),
                    "expected real question: '" + input + "'");
        }
    }

    @Test
    void handlesNullAndBlank() {
        assertFalse(CouncilService.isGreeting(null));
        assertFalse(CouncilService.isGreeting(""));
        assertFalse(CouncilService.isGreeting("   \n  "));
    }
}
