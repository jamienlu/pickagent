package com.pickagent.w2d1.core;

import java.util.List;
import java.util.Objects;

/** Immutable snapshot passed into a single model invocation. */
public record AgentContext(String input, List<Exchange> history, List<ToolDefinition> tools) {
    public AgentContext {
        Checks.nonBlank(input, "input");
        history = List.copyOf(history);
        tools = List.copyOf(tools);
    }

    public record Exchange(AgentDecision.ToolCall call, ToolResult result) {
        public Exchange {
            Objects.requireNonNull(call, "call");
            Objects.requireNonNull(result, "result");
            if (!call.callId().equals(result.callId())) {
                throw new IllegalArgumentException("tool result callId must match original callId");
            }
        }
    }
}
