package com.pickagent.w2d1.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Owns the bounded model -> tool -> model loop. No provider or transport knowledge. */
public final class AgentRuntime {
    private final AgentModelPort model;
    private final ToolRegistry tools;
    private final int maxSteps;

    /** A step is one model decision plus at most one serial tool execution. */
    public AgentRuntime(AgentModelPort model, ToolRegistry tools, int maxSteps) {
        this.model = Objects.requireNonNull(model, "model");
        this.tools = Objects.requireNonNull(tools, "tools");
        if (maxSteps < 1) {
            throw new IllegalArgumentException("maxSteps must be positive");
        }
        this.maxSteps = maxSteps;
    }

    public Result run(String input) {
        Checks.nonBlank(input, "input");
        List<AgentState> trace = new ArrayList<>(List.of(AgentState.START));
        List<AgentContext.Exchange> history = new ArrayList<>();
        List<AgentStep> steps = new ArrayList<>();
        var executedCallIds = new HashSet<String>();

        for (int step = 1; step <= maxSteps; step++) {
            trace.add(AgentState.MODEL);
            AgentDecision decision = Objects.requireNonNull(
                    model.decide(new AgentContext(input, history, tools.definitions())),
                    "model returned null decision");
            steps.add(new AgentStep(step, decision, Optional.empty()));
            if (decision instanceof AgentDecision.FinalAnswer answer) {
                trace.add(AgentState.FINAL);
                trace.add(AgentState.STOP);
                return new Completed(answer, trace, history, steps);
            }

            var call = (AgentDecision.ToolCall) decision;
            trace.add(AgentState.TOOL);
            if (executedCallIds.contains(call.callId())) {
                return stopped(StopReason.DUPLICATE_CALL_ID,
                        "duplicate callId: " + call.callId(), trace, history, steps);
            }
            // Do not execute a tool if no model turn remains to consume its output.
            if (step == maxSteps) {
                return stopped(StopReason.MAX_STEPS,
                        "maxSteps reached before tool execution: " + maxSteps, trace, history, steps);
            }

            ToolResult result;
            try {
                result = tools.execute(call);
            } catch (ToolRegistry.RejectedCall rejected) {
                StopReason reason = switch (rejected.reason()) {
                    case UNKNOWN_TOOL -> StopReason.UNKNOWN_TOOL;
                    case INVALID_ARGUMENTS -> StopReason.INVALID_ARGUMENTS;
                };
                return stopped(reason, rejected.getMessage(), trace, history, steps);
            } catch (ToolExecutionException failure) {
                trace.add(AgentState.STOP);
                return new ToolFailed(call, failure, trace, history, steps);
            }
            executedCallIds.add(call.callId());
            history.add(new AgentContext.Exchange(call, result));
            steps.set(steps.size() - 1, new AgentStep(step, decision, Optional.of(result)));
        }
        throw new AssertionError("bounded loop must return at its final turn");
    }

    private static Stopped stopped(StopReason reason, String detail,
                                   List<AgentState> trace, List<AgentContext.Exchange> history,
                                   List<AgentStep> steps) {
        trace.add(AgentState.STOP);
        return new Stopped(reason, detail, trace, history, steps);
    }

    public sealed interface Result permits Completed, Stopped, ToolFailed {
        List<AgentState> trace();
        List<AgentContext.Exchange> history();
        List<AgentStep> steps();

        default int stepsTaken() {
            return steps().size();
        }
    }

    public record Completed(AgentDecision.FinalAnswer answer, List<AgentState> trace,
                            List<AgentContext.Exchange> history, List<AgentStep> steps) implements Result {
        public Completed {
            Objects.requireNonNull(answer, "answer");
            trace = List.copyOf(trace);
            history = List.copyOf(history);
            steps = List.copyOf(steps);
        }
    }

    public record Stopped(StopReason reason, String detail, List<AgentState> trace,
                          List<AgentContext.Exchange> history, List<AgentStep> steps) implements Result {
        public Stopped {
            Objects.requireNonNull(reason, "reason");
            Checks.nonBlank(detail, "stop detail");
            trace = List.copyOf(trace);
            history = List.copyOf(history);
            steps = List.copyOf(steps);
        }
    }

    /** Expected execution failure, separate from validation/limit stops and final answers. */
    public record ToolFailed(AgentDecision.ToolCall call, ToolExecutionException failure,
                             List<AgentState> trace, List<AgentContext.Exchange> history,
                             List<AgentStep> steps) implements Result {
        public ToolFailed {
            Objects.requireNonNull(call, "call");
            Objects.requireNonNull(failure, "failure");
            trace = List.copyOf(trace);
            history = List.copyOf(history);
            steps = List.copyOf(steps);
        }
    }

    public enum StopReason {
        UNKNOWN_TOOL, INVALID_ARGUMENTS, MAX_STEPS, DUPLICATE_CALL_ID
    }
}
