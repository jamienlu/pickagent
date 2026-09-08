package io.github.jamielu.agent.tool;

import io.github.jamielu.agent.api.AgentDecision;
import io.github.jamielu.agent.api.ToolDefinition;
import io.github.jamielu.agent.api.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolRegistryBatchTest {
    @Test
    void invalidSecondCallPreventsEveryHandlerFromRunning() {
        AtomicInteger executions = new AtomicInteger();
        ToolRegistry registry = registry(executions, new ArrayList<>());

        var failure = assertThrows(ToolRegistry.RejectedCall.class,
                () -> registry.prepareAll(List.of(
                        call("call_1", "echo", "first"),
                        call("call_2", "unknown", "second"))));

        assertEquals(ToolRegistry.Rejection.UNKNOWN_TOOL, failure.reason());
        assertEquals(0, executions.get());
    }

    @Test
    void duplicateCallIdPreventsEveryHandlerFromRunning() {
        AtomicInteger executions = new AtomicInteger();
        ToolRegistry registry = registry(executions, new ArrayList<>());

        var failure = assertThrows(ToolRegistry.RejectedCall.class,
                () -> registry.prepareAll(List.of(
                        call("call_same", "echo", "first"),
                        call("call_same", "echo", "second"))));

        assertEquals(ToolRegistry.Rejection.DUPLICATE_CALL_ID, failure.reason());
        assertEquals(0, executions.get());
    }

    @Test
    void preparedBatchExecutesDeterministicallyInCallerOrder() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        List<String> observed = new ArrayList<>();
        ToolRegistry registry = registry(executions, observed);
        List<ToolRegistry.PreparedCall> prepared = registry.prepareAll(List.of(
                call("call_1", "echo", "first"),
                call("call_2", "echo", "second")));

        List<ToolResult> results = new ArrayList<>();
        for (ToolRegistry.PreparedCall preparedCall : prepared) {
            results.add(registry.execute(preparedCall));
        }

        assertEquals(List.of("first", "second"), observed);
        assertEquals(List.of("call_1", "call_2"),
                results.stream().map(ToolResult::callId).toList());
        assertEquals(2, executions.get());
    }

    @Test
    void preparedCallCannotBeExecutedByAnotherRegistry() {
        ToolRegistry first = registry(new AtomicInteger(), new ArrayList<>());
        ToolRegistry second = registry(new AtomicInteger(), new ArrayList<>());
        ToolRegistry.PreparedCall prepared = first.prepare(call("call_1", "echo", "value"));

        assertThrows(IllegalArgumentException.class, () -> second.execute(prepared));
    }

    private static ToolRegistry registry(AtomicInteger executions, List<String> observed) {
        ToolDefinition definition = new ToolDefinition(
                "echo", "Echo a value", Set.of("value"));
        return new ToolRegistry(List.of(new ToolRegistry.Registration(
                definition,
                arguments -> {
                    executions.incrementAndGet();
                    observed.add(arguments.get("value"));
                    return arguments.get("value");
                })));
    }

    private static AgentDecision.ToolCall call(
            String callId,
            String toolName,
            String value) {
        return new AgentDecision.ToolCall(
                callId, toolName, Map.of("value", value));
    }
}
