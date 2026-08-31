package com.pickagent.w1d5.core;

import java.util.Objects;

/**
 * 模型网关返回的供应商中立封闭结果类型。
 *
 * @author jamieLu
 * @since 2026-08-28
 */
public sealed interface ModelResult permits ModelResult.Completed, ModelResult.Refused,
        ModelResult.Incomplete, ModelResult.Failed {

    /**
     * 表示模型已经完整生成输出。
     *
     * @param content 最终模型文本
     */
    record Completed(String content) implements ModelResult {
        /** 校验完成结果必须包含文本。 */
        public Completed {
            Objects.requireNonNull(content, "content");
        }
    }

    /**
     * 表示模型明确拒绝请求。
     *
     * @param reason 拒绝原因
     */
    record Refused(String reason) implements ModelResult {
        /** 校验拒绝结果必须包含非空原因。 */
        public Refused {
            reason = requireReason(reason, "refusal reason");
        }
    }

    /**
     * 表示模型接受请求但未完整生成响应。
     *
     * @param reason 未完成原因，例如输出预算耗尽
     */
    record Incomplete(String reason) implements ModelResult {
        /** 校验未完成结果必须包含非空原因。 */
        public Incomplete {
            reason = requireReason(reason, "incomplete reason");
        }
    }

    /**
     * 表示 adapter 已归一化的运行失败。
     *
     * @param kind 失败分类
     * @param reason 失败原因
     */
    record Failed(FailureKind kind, String reason) implements ModelResult {
        /** 校验失败结果必须包含分类和非空原因。 */
        public Failed {
            Objects.requireNonNull(kind, "kind");
            reason = requireReason(reason, "failure reason");
        }
    }

    /** 模型网关运行失败分类。 */
    enum FailureKind {
        /** 网络、连接或超时等传输失败。 */
        TRANSPORT,
        /** 供应商服务、限流或认证等失败。 */
        PROVIDER,
        /** 响应结构或协议映射失败。 */
        PROTOCOL
    }

    /**
     * 校验结果原因必须包含可诊断文本。
     *
     * @param reason 原因文本
     * @param fieldName 用于异常信息的字段名称
     * @return 原始原因文本
     * @throws IllegalArgumentException 原因为空时抛出
     */
    private static String requireReason(String reason, String fieldName) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
        return reason;
    }
}
