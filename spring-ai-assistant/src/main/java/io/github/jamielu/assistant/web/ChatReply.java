package io.github.jamielu.assistant.web;

import io.github.jamielu.assistant.application.AssistantAnswer;

/**
 * 完整同步响应的增量兼容 JSON 契约。
 *
 * <p>既有 {@code content} 字段仍为必填。新增的可空字段明确表示未知，
 * 并允许旧客户端忽略这些扩展字段。</p>
 *
 * @param content 助手回答正文
 * @param responseId 可空的供应商响应标识
 * @param model 可空的供应商模型名称
 * @param usage 可空的 Token 用量
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
