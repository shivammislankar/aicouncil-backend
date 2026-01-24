package com.AI.aicouncil;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "gemini.api-key="   // explicitly disable Gemini in tests
})
class AicouncilApplicationTests {

    @Test
    void contextLoads() {
    }
}
