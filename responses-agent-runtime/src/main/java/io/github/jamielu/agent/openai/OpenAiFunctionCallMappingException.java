package io.github.jamielu.agent.openai;

import java.util.Objects;

/** 将异构 OpenAI 输出映射到核心调用时的明确失败。 */
public final class OpenAiFunctionCallMappingException extends IllegalArgumentException {
    /** 供程序化处理的稳定分类。 */
    private final Reason reason;

    /**
     * 创建不含嵌套解析失败的映射异常。
     *
     * @param reason 稳定失败分类
     * @param message 不包含秘密的诊断消息
     */
    public OpenAiFunctionCallMappingException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /**
     * 创建由供应商数据格式错误引起的映射异常。
     *
     * @param reason 稳定失败分类
     * @param message 不包含秘密的诊断消息
     * @param cause 底层解析失败
     */
    public OpenAiFunctionCallMappingException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /**
     * 返回机器可读的失败分类。
     *
     * @return 稳定失败分类
     */
    public Reason reason() {
        return reason;
    }

    /** 调用方无需解析异常文本即可处理的分类。 */
    public enum Reason {
        /** 输出条目中没有函数调用。 */
        NO_FUNCTION_CALL,
        /** 函数调用超过一个而核心端口只支持一个。 */
        MULTIPLE_FUNCTION_CALLS,
        /** 两个函数调用复用了同一关联标识。 */
        DUPLICATE_CALL_ID,
        /** 参数字段不是有效 JSON。 */
        MALFORMED_ARGUMENTS_JSON,
        /** 解析后的参数根节点不是 JSON 对象。 */
        ARGUMENTS_NOT_OBJECT,
        /** JSON 对象成员不是字符串。 */
        NON_STRING_ARGUMENT,
        /** 必需的调用标识或函数名称为空白。 */
        INVALID_FUNCTION_CALL_FIELD
    }
}


