package io.github.jamielu.assistant.web;

import io.github.jamielu.assistant.application.TokenUsage;

/** JSON token usage; nullable values mean unknown, never an inferred zero. */
public record TokenUsageReply(
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens) {

    static TokenUsageReply from(TokenUsage usage) {
        return usage == null ? null : new TokenUsageReply(
                usage.promptTokens(),
                usage.completionTokens(),
                usage.totalTokens());
    }
}
