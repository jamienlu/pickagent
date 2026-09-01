package com.pickagent.w2.core;

/**
 * Agent Runtime 的可观察生命周期状态。
 *
 * <p>状态转换由 Runtime 驱动，模型 adapter 无权直接修改。</p>
 *
 * @author jamieLu
 * @since 2026-08-31
 */
public enum AgentState {
    /** 一次运行开始。 */
    START,
    /** Runtime 正在请求模型决策。 */
    MODEL,
    /** Runtime 正在校验或执行工具调用。 */
    TOOL,
    /** 模型已经产生最终回答。 */
    FINAL,
    /** 运行已经结束。 */
    STOP
}
