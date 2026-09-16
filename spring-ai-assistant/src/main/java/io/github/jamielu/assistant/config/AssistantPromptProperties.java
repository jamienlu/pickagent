package io.github.jamielu.assistant.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Application-owned prompt policy bound from external configuration. */
@Validated
@ConfigurationProperties(prefix = "assistant.prompt")
public record AssistantPromptProperties(
        @NotBlank(message = "assistant.prompt.system must not be blank") String system) {
}
