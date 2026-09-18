package io.github.jamielu.assistant.web;

import io.github.jamielu.assistant.application.TokenUsage;

/**
 * JSON Token 用量；可空值表示未知，绝不推断为零。
 *
 * @param promptTokens 可空的提示词 Token 数
 * @param completionTokens 可空的生成内容 Token 数
 * @param totalTokens 可空的 Token 总数
 */
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
