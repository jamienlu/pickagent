package io.github.jamielu.assistant.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 由应用持有的通用文本生成策略。
 *
 * @param maxOutputTokens 单次模型响应允许生成的最大 Token 数
 */
@Validated
@ConfigurationProperties(prefix = "assistant.generation")
public record AssistantGenerationProperties(
        @NotNull(message = "assistant.generation.max-output-tokens must be configured")
        @Positive(message = "assistant.generation.max-output-tokens must be greater than zero")
        Integer maxOutputTokens) {
}
