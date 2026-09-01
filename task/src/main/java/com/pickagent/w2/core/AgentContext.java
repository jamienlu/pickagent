package com.pickagent.w2.core;

import java.util.List;
import java.util.Objects;

/**
 * 传递给一次模型调用的不可变 Agent 上下文快照。
 *
 * @param input 本次运行的原始用户输入
 * @param history 已成功完成的工具调用与结果历史
 * @param tools 当前允许模型使用的工具定义
 * @author jamieLu
 * @since 2026-08-31
 */
public record AgentContext(String input, List<Exchange> history, List<ToolDefinition> tools) {
    /**
     * 创建上下文快照，并复制历史和工具集合以防止外部修改。
     *
     * @throws IllegalArgumentException input 为空时抛出
     * @throws NullPointerException history、tools 或其元素为 {@code null} 时抛出
     */
    public AgentContext {
        Checks.nonBlank(input, "input");
        history = List.copyOf(history);
        tools = List.copyOf(tools);
    }

    /**
     * 一次已经成功执行且关联完整的工具交互。
     *
     * @param call 原始工具调用
     * @param result 使用相同 callId 返回的工具结果
     */
    public record Exchange(AgentDecision.ToolCall call, ToolResult result) {
        /**
         * 创建工具交互并校验调用结果关联关系。
         *
         * @throws NullPointerException call 或 result 为 {@code null} 时抛出
         * @throws IllegalArgumentException 调用和结果的 callId 不一致时抛出
         */
        public Exchange {
            Objects.requireNonNull(call, "call");
            Objects.requireNonNull(result, "result");
            if (!call.callId().equals(result.callId())) {
                throw new IllegalArgumentException("tool result callId must match original callId");
            }
        }
    }
}
