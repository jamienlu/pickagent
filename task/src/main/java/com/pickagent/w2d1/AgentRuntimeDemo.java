package com.pickagent.w2d1;

import com.pickagent.w2d1.core.AgentDecision;
import com.pickagent.w2d1.core.AgentRuntime;
import com.pickagent.w2d1.core.ToolRegistry;
import com.pickagent.w2d1.infrastructure.ReplayAgentModel;
import com.pickagent.w2d1.infrastructure.ReplayOrderTool;

import java.io.PrintStream;
import java.util.List;
import java.util.stream.Collectors;

public final class AgentRuntimeDemo {
    private AgentRuntimeDemo() {
    }

    public static void main(String[] args) {
        run(System.out);
    }

    /** Caller-supplied output keeps the demo testable without replacing System.out. */
    public static void run(PrintStream out) {
        var registry = new ToolRegistry(List.of(
                new ToolRegistry.Registration(ReplayOrderTool.DEFINITION, new ReplayOrderTool())));
        var runtime = new AgentRuntime(new ReplayAgentModel(), registry, 3);
        AgentRuntime.Result result = runtime.run("What is the status of order ORD-001?");
        for (var step : result.steps()) {
            if (step.decision() instanceof AgentDecision.ToolCall call) {
                out.println("step=" + step.number() + " decision=ToolCall");
                out.println("  tool call: callId=" + call.callId()
                        + ", tool=" + call.toolName() + ", arguments=" + call.arguments());
                step.observation().ifPresent(observation -> out.println(
                        "  observation: callId=" + observation.callId() + ", output=" + observation.output()));
            } else {
                var answer = (AgentDecision.FinalAnswer) step.decision();
                out.println("step=" + step.number() + " decision=FinalAnswer");
                out.println("  final answer: " + answer.text());
            }
        }
        out.println("TRACE: " + result.trace().stream()
                .map(Enum::name).collect(Collectors.joining(" -> ")));
        if (!(result instanceof AgentRuntime.Completed completed)) {
            throw new IllegalStateException("Replay must complete: " + result);
        }
        out.println("result=Completed steps=" + completed.stepsTaken());
    }
}
