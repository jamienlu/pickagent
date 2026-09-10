package io.github.jamielu.agent.openai;

/**
 * 单次 Responses 请求的显式安全选项。
 *
 * @param maxOutputTokens 可见输出与推理 Token 的合计上限
 * @param store 是否允许服务端保存响应以使用 {@code previous_response_id} 续接
 */
public record OpenAiResponseOptions(long maxOutputTokens, boolean store) {
    /** 创建并校验请求选项。 */
    public OpenAiResponseOptions {
        if (maxOutputTokens < 1) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }
    }

    /**
     * 返回适用于有状态工具循环的默认选项。
     *
     * @return 默认单响应选项
     */
    public static OpenAiResponseOptions defaults() {
        return new OpenAiResponseOptions(1024, true);
    }
}
