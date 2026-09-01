package com.pickagent.w2.core;

import java.util.Objects;
import java.util.Optional;

/**
 * 一次模型决策及其可选的成功工具观察。
 *
 * @param number 从 1 开始的步骤编号
 * @param decision 本步模型决策
 * @param observation 工具成功执行后的观察；最终回答或失败步骤为空
 * @author jamieLu
 * @since 2026-08-31
 */
public record AgentStep(int number, AgentDecision decision, Optional<ToolResult> observation) {
    /**
     * 创建步骤并校验编号以及工具结果关联关系。
     *
     * @throws IllegalArgumentException number 非正数，或 observation 与工具调用不匹配时抛出
     * @throws NullPointerException decision 或 observation 为 {@code null} 时抛出
     */
    public AgentStep {
        if (number < 1) {
            throw new IllegalArgumentException("step number must be positive");
        }
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(observation, "observation");
        if (observation.isPresent()) {
            if (!(decision instanceof AgentDecision.ToolCall call)
                    || !call.callId().equals(observation.get().callId())) {
                throw new IllegalArgumentException("observation must match the step's tool callId");
            }
        }
    }
}
