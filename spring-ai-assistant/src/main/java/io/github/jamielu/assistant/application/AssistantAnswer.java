package io.github.jamielu.assistant.application;

import java.util.Objects;

/**
 * Complete synchronous assistant answer owned by the application.
 *
 * <p>A {@code null} response ID, model, or usage means that the provider did not
 * supply that metadata. Unknown metadata must not be replaced with synthetic
 * values.</p>
 */
public record AssistantAnswer(
        String message,
        String responseId,
        String model,
        TokenUsage usage) {

    /** Creates an answer whose message is always present. */
    public AssistantAnswer {
        Objects.requireNonNull(message, "message");
    }
}
