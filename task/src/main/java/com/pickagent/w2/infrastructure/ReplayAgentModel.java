package com.pickagent.w2.infrastructure;

import com.pickagent.w2.core.AgentContext;
import com.pickagent.w2.core.AgentDecision;
import com.pickagent.w2.core.AgentModelPort;

import java.util.Map;

/**
 * 用于离线测试的确定性无状态模型 adapter。
 *
 * <p>首次调用固定请求订单查询工具，收到预期观察后返回最终回答；不访问真实模型服务。</p>
 *
 * @author jamieLu
 * @since 2026-08-31
 */
public final class ReplayAgentModel implements AgentModelPort {
    /** 创建无状态 Replay 模型 adapter。 */
    public ReplayAgentModel() {
    }

    /**
     * 根据固定对话轨迹产生下一次决策。
     *
     * @param context 当前 Agent 上下文
     * @return 固定工具调用或基于工具结果的最终回答
     * @throws IllegalStateException 历史不符合预设 Replay 轨迹时抛出
     */
    @Override
    public AgentDecision decide(AgentContext context) {
        var expectedCall = new AgentDecision.ToolCall(
                "call_order_001", "lookup_order", Map.of("orderId", "ORD-001"));
        if (context.history().isEmpty()) {
            return expectedCall;
        }
        if (context.history().size() != 1 || !context.history().get(0).call().equals(expectedCall)) {
            throw new IllegalStateException("unexpected replay conversation");
        }
        return new AgentDecision.FinalAnswer(
                "Replay answer: " + context.history().get(0).result().output());
    }
}
