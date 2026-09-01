package com.pickagent.w2.core;

import java.util.Map;

/**
 * 单次模型调用产生的供应商中立决策。
 *
 * <p>当前版本只支持最终回答或一个串行工具调用，不表达并行工具调用。</p>
 *
 * @author jamieLu
 * @since 2026-08-31
 */
public sealed interface AgentDecision permits AgentDecision.FinalAnswer, AgentDecision.ToolCall {
    /**
     * 模型面向用户给出的最终回答。
     *
     * @param text 非空白回答文本
     */
    record FinalAnswer(String text) implements AgentDecision {
        /**
         * 创建最终回答。
         *
         * @throws IllegalArgumentException text 为空时抛出
         */
        public FinalAnswer {
            Checks.nonBlank(text, "final answer");
        }
    }

    /**
     * 模型建议执行的一次工具调用。
     *
     * <p>当前最小契约只允许字符串参数，不泄漏 SDK 或 JSON 节点类型。</p>
     *
     * @param callId 供应商工具调用关联标识
     * @param toolName 待调用工具名称
     * @param arguments 工具参数不可变快照
     */
    record ToolCall(String callId, String toolName, Map<String, String> arguments)
            implements AgentDecision {
        /**
         * 创建工具调用并复制参数映射。
         *
         * @throws IllegalArgumentException callId 或 toolName 为空时抛出
         * @throws NullPointerException arguments 或其键值为 {@code null} 时抛出
         */
        public ToolCall {
            Checks.nonBlank(callId, "callId");
            Checks.nonBlank(toolName, "toolName");
            arguments = Map.copyOf(arguments);
        }
    }
}
