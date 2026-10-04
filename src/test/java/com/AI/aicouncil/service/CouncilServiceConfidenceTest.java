package com.AI.aicouncil.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CouncilServiceConfidenceTest {

    @Test
    void extractsConfidenceAndStripsLine() {
        String raw = """
                ## Final Recommendation
                Build the web app first.

                CONFIDENCE: 78
                """;

        CouncilService.Synthesis result = CouncilService.parseSynthesis(raw);

        assertEquals(78, result.confidence());
        assertTrue(result.answer().startsWith("## Final Recommendation"));
        assertTrue(result.answer().endsWith("web app first."));
        assertFalse(result.answer().toUpperCase().contains("CONFIDENCE:"));
    }

    @Test
    void handlesLowerCasePercentAndFractionFormats() {
        assertEquals(45, CouncilService.parseSynthesis("Answer.\nconfidence: 45%\n").confidence());
        assertEquals(95, CouncilService.parseSynthesis("Answer.\nCONFIDENCE: 95/100\n").confidence());
        assertEquals(60, CouncilService.parseSynthesis("Answer.\nConfidence:  60 .\n").confidence());
    }

    @Test
    void fallsBackToDefaultWhenLineMissing() {
        String raw = "Just a plain answer with no confidence line.";

        CouncilService.Synthesis result = CouncilService.parseSynthesis(raw);

        assertEquals(85, result.confidence());
        assertEquals(raw, result.answer());
    }

    @Test
    void clampsOutOfRangeValues() {
        assertEquals(100, CouncilService.parseSynthesis("A\nCONFIDENCE: 150\n").confidence());
        assertEquals(0, CouncilService.parseSynthesis("A\nCONFIDENCE: 0\n").confidence());
    }

    @Test
    void usesLastConfidenceLineWhenModelAddsTrailingText() {
        String raw = "The answer.\nCONFIDENCE: 72\nHope this helps!";

        CouncilService.Synthesis result = CouncilService.parseSynthesis(raw);

        assertEquals(72, result.confidence());
        assertFalse(result.answer().contains("CONFIDENCE:"));
        assertTrue(result.answer().contains("Hope this helps!"));
    }

    @Test
    void handlesNullAndBlankInput() {
        assertEquals(85, CouncilService.parseSynthesis(null).confidence());
        assertEquals("", CouncilService.parseSynthesis(null).answer());
        assertEquals(85, CouncilService.parseSynthesis("   \n  ").confidence());
    }
}
