package io.github.jamielu.assistant.web;

import io.github.jamielu.assistant.application.AssistantAnswer;

/**
 * Additive JSON contract for a complete synchronous response.
 *
 * <p>The existing {@code content} field remains required. Nullable added fields
 * explicitly mean unknown and allow older clients to ignore the extension.</p>
 */
public record ChatReply(
        String content,
        String responseId,
        String model,
        TokenUsageReply usage) {

    static ChatReply from(AssistantAnswer answer) {
        return new ChatReply(
                answer.message(),
                answer.responseId(),
                answer.model(),
                TokenUsageReply.from(answer.usage()));
    }
}
