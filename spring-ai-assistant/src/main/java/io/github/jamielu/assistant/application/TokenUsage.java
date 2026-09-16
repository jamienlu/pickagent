package io.github.jamielu.assistant.application;

/**
 * Provider-neutral token usage for one completed synchronous response.
 *
 * <p>Each nullable field is independently unknown when absent. Zero is retained
 * only when it was explicitly reported by a non-empty provider usage object.</p>
 */
public record TokenUsage(
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens) {
}
