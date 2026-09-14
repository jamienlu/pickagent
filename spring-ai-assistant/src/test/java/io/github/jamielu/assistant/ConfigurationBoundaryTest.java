package io.github.jamielu.assistant;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationBoundaryTest {
    @Test
    void productionConfigurationContainsOnlyEnvironmentPlaceholders() throws IOException {
        String configuration;
        try (var input = getClass().getResourceAsStream("/application.properties")) {
            configuration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(configuration.contains("spring.ai.openai.api-key=${OPENAI_API_KEY}"));
        assertTrue(configuration.contains("spring.ai.openai.chat.model=${OPENAI_MODEL}"));
        assertTrue(configuration.contains("spring.ai.openai.base-url=${OPENAI_BASE_URL}"));
        assertFalse(configuration.matches("(?s).*sk-[A-Za-z0-9_-]{16,}.*"));
    }
}
