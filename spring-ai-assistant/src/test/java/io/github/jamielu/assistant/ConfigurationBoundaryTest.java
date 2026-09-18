package io.github.jamielu.assistant;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证生产配置只保存环境变量占位符，不包含静态凭据。 */
class ConfigurationBoundaryTest {
    /** 验证提示词、超时与 OpenAI 配置均从环境注入，且不存在密钥形态文本。 */
    @Test
    void productionConfigurationContainsOnlyEnvironmentPlaceholders() throws IOException {
        String configuration;
        try (var input = getClass().getResourceAsStream("/application.properties")) {
            configuration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(configuration.contains("assistant.prompt.system=${ASSISTANT_SYSTEM_PROMPT:"));
        assertTrue(configuration.contains(
                "assistant.stream.signal-timeout=${ASSISTANT_STREAM_SIGNAL_TIMEOUT:30s}"));
        assertTrue(configuration.contains("spring.ai.openai.api-key=${OPENAI_API_KEY}"));
        assertTrue(configuration.contains("spring.ai.openai.chat.model=${OPENAI_MODEL}"));
        assertTrue(configuration.contains("spring.ai.openai.base-url=${OPENAI_BASE_URL}"));
        assertFalse(configuration.matches("(?s).*sk-[A-Za-z0-9_-]{16,}.*"));
    }
}
