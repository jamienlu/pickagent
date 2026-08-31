package com.pickagent.w1d5.core;

import java.util.Objects;

/**
 * 事件生成用例对调用者暴露的供应商中立结果。
 *
 * @author jamieLu
 * @since 2026-08-28
 */
public sealed interface GenerateEventResult permits GenerateEventResult.Generated,
        GenerateEventResult.Refused, GenerateEventResult.Incomplete, GenerateEventResult.Failed {

    /**
     * 表示事件已成功生成并通过应用契约校验。
     *
     * @param event 有效事件
     */
    record Generated(EventData event) implements GenerateEventResult {
        /** 校验成功结果必须包含事件。 */
        public Generated {
            Objects.requireNonNull(event, "event");
        }
    }

    /**
     * 表示模型明确拒绝处理请求。
     *
     * @param reason 拒绝原因
     */
    record Refused(String reason) implements GenerateEventResult {
        /** 校验拒绝结果必须包含原因。 */
        public Refused {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * 表示模型响应未完整生成。
     *
     * @param reason 未完成原因
     */
    record Incomplete(String reason) implements GenerateEventResult {
        /** 校验未完成结果必须包含原因。 */
        public Incomplete {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * 表示网关故障或模型输出未通过应用契约。
     *
     * @param kind 应用层失败分类
     * @param reason 失败原因
     */
    record Failed(FailureKind kind, String reason) implements GenerateEventResult {
        /** 校验失败结果必须包含分类和原因。 */
        public Failed {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** 应用层事件生成失败分类。 */
    enum FailureKind {
        /** 模型网关传输失败。 */
        GATEWAY_TRANSPORT,
        /** 模型供应商拒绝或服务失败。 */
        GATEWAY_PROVIDER,
        /** 供应商响应协议不符合 adapter 预期。 */
        GATEWAY_PROTOCOL,
        /** 已完成文本不符合事件契约。 */
        INVALID_MODEL_OUTPUT
    }
}
