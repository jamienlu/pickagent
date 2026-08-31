package com.pickagent.w2d1;

import com.pickagent.w2d1.core.AgentRuntime;
import com.pickagent.w2d1.core.ToolRegistry;
import com.pickagent.w2d1.infrastructure.ReplayAgentModel;
import com.pickagent.w2d1.infrastructure.ReplayOrderTool;

import java.util.List;
import java.util.stream.Collectors;

public final class AgentRuntimeDemo {
    private AgentRuntimeDemo() {
    }

    public static void main(String[] args) {
        var registry = new ToolRegistry(List.of(
                new ToolRegistry.Registration(ReplayOrderTool.DEFINITION, new ReplayOrderTool())));
        var runtime = new AgentRuntime(new ReplayAgentModel(), registry, 3);
        AgentRuntime.Result result = runtime.run("What is the status of order ORD-001?");
        System.out.println("TRACE: " + result.trace().stream()
                .map(Enum::name).collect(Collectors.joining(" -> ")));
        if (!(result instanceof AgentRuntime.Completed completed)) {
            throw new IllegalStateException("Replay must complete: " + result);
        }
        for (var exchange : completed.history()) {
            System.out.println("ToolCall: callId=" + exchange.call().callId()
                    + ", tool=" + exchange.call().toolName() + ", arguments=" + exchange.call().arguments());
            System.out.println("ToolResult: callId=" + exchange.result().callId()
                    + ", output=" + exchange.result().output());
        }
        System.out.println("FinalAnswer: " + completed.answer().text());
    }
}
