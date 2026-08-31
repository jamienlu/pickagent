package com.pickagent.w2d1.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Owns the bounded model -> tool -> model loop. No provider or transport knowledge. */
public final class AgentRuntime {
    private final AgentModelPort model;
    private final ToolRegistry tools;
    private final int maxModelTurns;

    public AgentRuntime(AgentModelPort model, ToolRegistry tools, int maxModelTurns) {
        this.model = Objects.requireNonNull(model, "model");
        this.tools = Objects.requireNonNull(tools, "tools");
        if (maxModelTurns < 1) {
            throw new IllegalArgumentException("maxModelTurns must be positive");
        }
        this.maxModelTurns = maxModelTurns;
    }

    public Result run(String input) {
        Checks.nonBlank(input, "input");
        List<AgentState> trace = new ArrayList<>(List.of(AgentState.START));
        List<AgentContext.Exchange> history = new ArrayList<>();
        var executedCallIds = new HashSet<String>();

        for (int turn = 1; turn <= maxModelTurns; turn++) {
            trace.add(AgentState.MODEL);
            AgentDecision decision = Objects.requireNonNull(
                    model.decide(new AgentContext(input, history, tools.definitions())),
                    "model returned null decision");
            if (decision instanceof AgentDecision.FinalAnswer answer) {
                trace.add(AgentState.FINAL);
                trace.add(AgentState.STOP);
                return new Completed(answer, trace, history);
            }

            var call = (AgentDecision.ToolCall) decision;
            trace.add(AgentState.TOOL);
            if (executedCallIds.contains(call.callId())) {
                return stopped(StopReason.DUPLICATE_CALL_ID,
                        "duplicate callId: " + call.callId(), trace, history);
            }
            // Do not execute a tool if no model turn remains to consume its output.
            if (turn == maxModelTurns) {
                return stopped(StopReason.TURN_LIMIT,
                        "model turn limit reached before tool execution: " + maxModelTurns, trace, history);
            }

            ToolResult result;
            try {
                result = tools.execute(call);
            } catch (ToolRegistry.RejectedCall rejected) {
                StopReason reason = switch (rejected.reason()) {
                    case UNKNOWN_TOOL -> StopReason.UNKNOWN_TOOL;
                    case INVALID_ARGUMENTS -> StopReason.INVALID_ARGUMENTS;
                };
                return stopped(reason, rejected.getMessage(), trace, history);
            }
            executedCallIds.add(call.callId());
            history.add(new AgentContext.Exchange(call, result));
        }
        throw new AssertionError("bounded loop must return at its final turn");
    }

    private static Stopped stopped(StopReason reason, String detail,
                                   List<AgentState> trace, List<AgentContext.Exchange> history) {
        trace.add(AgentState.STOP);
        return new Stopped(reason, detail, trace, history);
    }

    public sealed interface Result permits Completed, Stopped {
        List<AgentState> trace();
        List<AgentContext.Exchange> history();
    }

    public record Completed(AgentDecision.FinalAnswer answer, List<AgentState> trace,
                            List<AgentContext.Exchange> history) implements Result {
        public Completed {
            Objects.requireNonNull(answer, "answer");
            trace = List.copyOf(trace);
            history = List.copyOf(history);
        }
    }

    public record Stopped(StopReason reason, String detail, List<AgentState> trace,
                          List<AgentContext.Exchange> history) implements Result {
        public Stopped {
            Objects.requireNonNull(reason, "reason");
            Checks.nonBlank(detail, "stop detail");
            trace = List.copyOf(trace);
            history = List.copyOf(history);
        }
    }

    public enum StopReason {
        UNKNOWN_TOOL, INVALID_ARGUMENTS, TURN_LIMIT, DUPLICATE_CALL_ID
    }
}
