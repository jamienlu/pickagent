package io.github.jamielu.assistant.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 从外部配置绑定、由应用持有的提示词策略。
 *
 * @param system 系统提示词
 */
@Validated
@ConfigurationProperties(prefix = "assistant.prompt")
public record AssistantPromptProperties(
        @NotBlank(message = "assistant.prompt.system must not be blank") String system) {
}
