package com.pickagent.w2.core;

/**
 * Agent 核心定义的单次模型决策出站端口。
 *
 * <p>实现负责将供应商输入输出转换为核心类型，但不得执行工具、驱动循环或修改运行预算。</p>
 *
 * @author jamieLu
 * @since 2026-08-31
 */
@FunctionalInterface
public interface AgentModelPort {
    /**
     * 根据当前不可变上下文产生一次模型决策。
     *
     * @param context 当前输入、历史观察和可用工具快照
     * @return 最终回答或单个工具调用决策，不允许返回 {@code null}
     */
    AgentDecision decide(AgentContext context);
}
