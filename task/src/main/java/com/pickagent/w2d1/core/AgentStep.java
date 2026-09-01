package com.pickagent.w2d1.core;

import java.util.Objects;
import java.util.Optional;

/** One model decision and, if executed successfully, its tool observation. */
public record AgentStep(int number, AgentDecision decision, Optional<ToolResult> observation) {
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
