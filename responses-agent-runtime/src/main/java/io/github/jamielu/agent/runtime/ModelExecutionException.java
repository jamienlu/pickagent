package io.github.jamielu.agent.runtime;

import io.github.jamielu.agent.internal.Arguments;
import io.github.jamielu.agent.reliability.FailureKind;

import java.util.Objects;

/** 模型出站端口已归类的可预期失败。 */
public final class ModelExecutionException extends RuntimeException {
    /** 供应商中立失败分类。 */
    private final FailureKind kind;

    /**
     * 创建模型执行失败。
     *
     * @param kind 供应商中立失败分类
     * @param message 可审计且不包含密钥的错误说明
     * @param cause 底层异常
     */
    public ModelExecutionException(FailureKind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = Objects.requireNonNull(kind, "kind");
        Arguments.nonBlank(message, "model failure message");
    }

    /**
     * 返回供应商中立失败分类。
     *
     * @return 供应商中立失败分类
     */
    public FailureKind kind() {
        return kind;
    }
}
