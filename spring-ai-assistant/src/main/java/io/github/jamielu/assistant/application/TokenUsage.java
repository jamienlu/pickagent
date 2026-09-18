package io.github.jamielu.assistant.application;

/**
 * 一次完整同步响应中与供应商无关的 Token 用量。
 *
 * <p>每个可空字段缺失时都独立表示未知。只有非空供应商用量对象明确报告零时，
 * 才保留数值零。</p>
 *
 * @param promptTokens 可空的提示词 Token 数
 * @param completionTokens 可空的生成内容 Token 数
 * @param totalTokens 可空的 Token 总数
 */
public record TokenUsage(
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens) {
}
