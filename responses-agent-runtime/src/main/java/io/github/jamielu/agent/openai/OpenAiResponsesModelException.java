package io.github.jamielu.agent.openai;

import java.util.Objects;

/** 有状态 Responses 模型适配器契约中的类型化失败。 */
public final class OpenAiResponsesModelException extends IllegalStateException {
    /** 无需解析消息即可分支处理的稳定分类。 */
    private final Reason reason;

    /**
     * 创建适配器失败。
     *
     * @param reason 稳定失败分类
     * @param message 不包含秘密的诊断消息
     */
    public OpenAiResponsesModelException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /**
     * 返回稳定失败分类。
     *
     * @return 稳定失败分类
     */
    public Reason reason() {
        return reason;
    }

    /** 调用方无需解析消息即可处理的适配器失败。 */
    public enum Reason {
        /** 续接上下文与适配器待处理调用不匹配。 */
        CONTEXT_MISMATCH,
        /** 供应商返回空白响应标识。 */
        INVALID_RESPONSE_ID,
        /** 终态响应没有可见输出文本。 */
        MISSING_FINAL_TEXT,
        /** 终态响应包含不支持的输出条目。 */
        UNEXPECTED_FINAL_OUTPUT_ITEM
    }
}
