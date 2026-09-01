package com.pickagent.w2.core;

import java.util.Map;

/** One normalized model decision. This first version supports one serial tool call. */
public sealed interface AgentDecision permits AgentDecision.FinalAnswer, AgentDecision.ToolCall {
    record FinalAnswer(String text) implements AgentDecision {
        public FinalAnswer {
            Checks.nonBlank(text, "final answer");
        }
    }

    /** Arguments are required strings in this minimal contract, not SDK/JSON values. */
    record ToolCall(String callId, String toolName, Map<String, String> arguments)
            implements AgentDecision {
        public ToolCall {
            Checks.nonBlank(callId, "callId");
            Checks.nonBlank(toolName, "toolName");
            arguments = Map.copyOf(arguments);
        }
    }
}
