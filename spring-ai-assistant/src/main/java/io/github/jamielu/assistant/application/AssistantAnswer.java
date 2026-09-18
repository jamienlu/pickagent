package io.github.jamielu.assistant.application;

import java.util.Objects;

/**
 * 由应用层定义的完整同步助手回答。
 *
 * <p>响应 ID、模型或用量为 {@code null}，表示供应商没有提供相应元数据。
 * 未知元数据不得替换为人工构造的值。</p>
 *
 * @param message 助手回答正文
 * @param responseId 可空的供应商响应标识
 * @param model 可空的供应商模型名称
 * @param usage 可空的 Token 用量
 */
public record AssistantAnswer(
        String message,
        String responseId,
        String model,
        TokenUsage usage) {

    /** 创建回答，并保证消息内容始终存在。 */
    public AssistantAnswer {
        Objects.requireNonNull(message, "message");
    }
}
