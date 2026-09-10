package io.github.jamielu.agent.openai;

import java.util.Objects;

/** Responses 输出无法无损或正确关联地续接时抛出的异常。 */
public final class OpenAiResponseLedgerException extends IllegalArgumentException {
    /** 稳定的协议失败分类。 */
    private final Reason reason;

    /**
     * 创建协议账本失败。
     *
     * @param reason 稳定协议失败分类
     * @param message 不包含秘密的诊断消息
     */
    public OpenAiResponseLedgerException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /**
     * 返回机器可读的失败分类。
     *
     * @return 稳定协议失败分类
     */
    public Reason reason() {
        return reason;
    }

    /** 构建续接请求前强制执行的快速失败原因。 */
    public enum Reason {
        /** 不存在可接收结果的函数调用。 */
        NO_FUNCTION_CALL,
        /** 函数调用关联标识为空白。 */
        INVALID_CALL_ID,
        /** 同一函数调用关联标识重复出现。 */
        DUPLICATE_CALL_ID,
        /** 已完成结果批次没有为每个调用提供一个结果。 */
        RESULT_COUNT_MISMATCH,
        /** 结果引用了输出历史中的另一个调用。 */
        CALL_ID_MISMATCH,
        /** 输出条目超出账本明确支持的集合。 */
        UNKNOWN_OUTPUT_ITEM,
        /** 最终回放响应包含非终态协议条目。 */
        UNEXPECTED_FINAL_OUTPUT_ITEM
    }
}


