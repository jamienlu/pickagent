package com.pickagent.w2d1.infrastructure;

import com.pickagent.w2d1.core.AgentContext;
import com.pickagent.w2d1.core.AgentDecision;
import com.pickagent.w2d1.core.AgentModelPort;

import java.util.Map;

/** Stateless fixture: one call per invocation, then an answer based on the observed result. */
public final class ReplayAgentModel implements AgentModelPort {
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
