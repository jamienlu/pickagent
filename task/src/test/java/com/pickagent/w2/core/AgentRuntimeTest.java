package com.pickagent.w2.core;

import com.pickagent.w2.infrastructure.ReplayAgentModel;
import com.pickagent.w2.infrastructure.ReplayOrderTool;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AgentRuntimeTest {
    @Test
    void toolCallThenResultThenFinalAnswerPreservesContextAndOriginalCallId() {
        List<AgentContext> invocations = new ArrayList<>();
        ReplayAgentModel replay = new ReplayAgentModel();
        AgentModelPort recordingModel = context -> {
            invocations.add(context);
            return replay.decide(context);
        };
        var runtime = new AgentRuntime(recordingModel, registry(new ReplayOrderTool()), 3);

        var completed = assertInstanceOf(AgentRuntime.Completed.class, runtime.run("Find ORD-001"));

        assertEquals("Replay answer: Order ORD-001: SHIPPED", completed.answer().text());
        assertEquals(List.of(AgentState.START, AgentState.MODEL, AgentState.TOOL,
                AgentState.MODEL, AgentState.FINAL, AgentState.STOP), completed.trace());
        assertEquals(2, invocations.size());
        assertTrue(invocations.get(0).history().isEmpty(), "earlier snapshots must stay immutable");
        AgentContext secondRequest = invocations.get(1);
        assertEquals("Find ORD-001", secondRequest.input());
        assertEquals(List.of(ReplayOrderTool.DEFINITION), secondRequest.tools());
        assertEquals(1, secondRequest.history().size());
        var exchange = secondRequest.history().get(0);
        assertEquals("call_order_001", exchange.call().callId());
        assertEquals(exchange.call().callId(), exchange.result().callId());
        assertEquals(Map.of("orderId", "ORD-001"), exchange.call().arguments());
        assertEquals("Order ORD-001: SHIPPED", exchange.result().output());
        assertEquals(completed.history(), secondRequest.history());
        assertEquals(2, completed.stepsTaken());
        assertEquals(new AgentStep(1, exchange.call(), Optional.of(exchange.result())), completed.steps().get(0));
        assertEquals(new AgentStep(2, completed.answer(), Optional.empty()), completed.steps().get(1));
        assertEquals(completed, runtime.run("Find ORD-001"), "Replay is repeatable across runs");
    }

    @Test
    void directFinalAnswerDoesNotExecuteAnyTool() {
        AtomicInteger toolCalls = new AtomicInteger();
        var runtime = new AgentRuntime(context -> new AgentDecision.FinalAnswer("Already known"),
                registry(args -> {
                    toolCalls.incrementAndGet();
                    return "must not run";
                }), 1);

        var result = assertInstanceOf(AgentRuntime.Completed.class, runtime.run("Question"));

        assertEquals("Already known", result.answer().text());
        assertEquals(List.of(AgentState.START, AgentState.MODEL, AgentState.FINAL, AgentState.STOP),
                result.trace());
        assertEquals(0, toolCalls.get());
        assertTrue(result.history().isEmpty());
        assertEquals(1, result.stepsTaken());
        assertEquals(new AgentDecision.FinalAnswer("Already known"), result.steps().get(0).decision());
    }

    @Test
    void unknownToolStopsBeforeExecutionOrAnotherModelInvocation() {
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicInteger toolCalls = new AtomicInteger();
        var runtime = new AgentRuntime(context -> {
            modelCalls.incrementAndGet();
            return new AgentDecision.ToolCall("call-unknown", "not_registered", Map.of());
        }, registry(args -> {
            toolCalls.incrementAndGet();
            return "must not run";
        }), 3);

        var stopped = assertInstanceOf(AgentRuntime.Stopped.class, runtime.run("Question"));

        assertEquals(AgentRuntime.StopReason.UNKNOWN_TOOL, stopped.reason());
        assertEquals("unknown tool: not_registered", stopped.detail());
        assertEquals(1, modelCalls.get());
        assertEquals(0, toolCalls.get());
        assertEquals(List.of(AgentState.START, AgentState.MODEL, AgentState.TOOL, AgentState.STOP),
                stopped.trace());
    }

    @Test
    void missingArgumentStopsBeforeToolExecution() {
        assertInvalidArguments(Map.of(), "missing=[orderId], extra=[]");
    }

    @Test
    void extraArgumentStopsBeforeToolExecution() {
        assertInvalidArguments(Map.of("orderId", "ORD-001", "admin", "true"), "missing=[], extra=[admin]");
    }

    @Test
    void blankArgumentStopsBeforeToolExecution() {
        assertInvalidArguments(Map.of("orderId", " "), "blank argument: orderId");
    }

    @Test
    void duplicateCallIdDoesNotExecuteToolTwice() {
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicInteger toolCalls = new AtomicInteger();
        var runtime = new AgentRuntime(context -> {
            modelCalls.incrementAndGet();
            return call("same-call");
        }, registry(args -> {
            toolCalls.incrementAndGet();
            return "SHIPPED";
        }), 4);

        var stopped = assertInstanceOf(AgentRuntime.Stopped.class, runtime.run("Question"));

        assertEquals(AgentRuntime.StopReason.DUPLICATE_CALL_ID, stopped.reason());
        assertEquals("duplicate callId: same-call", stopped.detail());
        assertEquals(2, modelCalls.get());
        assertEquals(1, toolCalls.get());
        assertEquals(1, stopped.history().size());
    }

    @Test
    void runtimeHonorsMaxStepsAndDoesNotExecuteUnconsumableToolCall() {
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicInteger toolCalls = new AtomicInteger();
        var runtime = new AgentRuntime(context -> call("call-" + modelCalls.incrementAndGet()),
                registry(args -> {
                    toolCalls.incrementAndGet();
                    return "SHIPPED";
                }), 2);

        var stopped = assertInstanceOf(AgentRuntime.Stopped.class, runtime.run("Question"));

        assertEquals(AgentRuntime.StopReason.MAX_STEPS, stopped.reason());
        assertEquals("maxSteps reached before tool execution: 2", stopped.detail());
        assertEquals(2, stopped.stepsTaken());
        assertEquals(2, stopped.steps().get(1).number());
        assertTrue(stopped.steps().get(1).observation().isEmpty());
        assertEquals(2, modelCalls.get());
        assertEquals(1, toolCalls.get());
        assertEquals(List.of(AgentState.START, AgentState.MODEL, AgentState.TOOL,
                AgentState.MODEL, AgentState.TOOL, AgentState.STOP), stopped.trace());
    }

    @Test
    void modelProgrammingErrorIsNotDisguisedAsExpectedStop() {
        var bug = new IllegalStateException("model mapping bug");
        var runtime = new AgentRuntime(context -> {
            throw bug;
        }, registry(new ReplayOrderTool()), 3);

        assertSame(bug, assertThrows(IllegalStateException.class, () -> runtime.run("Question")));
    }

    @Test
    void toolProgrammingErrorIsNotDisguisedAsValidationFailure() {
        var bug = new IllegalStateException("tool implementation bug");
        var runtime = new AgentRuntime(context -> call("call-bug"), registry(args -> {
            throw bug;
        }), 3);

        assertSame(bug, assertThrows(IllegalStateException.class, () -> runtime.run("Question")));
    }

    @Test
    void exchangeRejectsMismatchedCallId() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> new AgentContext.Exchange(call("original"), new ToolResult("wrong", "SHIPPED")));
        assertEquals("tool result callId must match original callId", error.getMessage());
    }

    @Test
    void registryRejectsDuplicateToolRegistration() {
        var registration = new ToolRegistry.Registration(
                ReplayOrderTool.DEFINITION, new ReplayOrderTool());
        var error = assertThrows(IllegalArgumentException.class,
                () -> new ToolRegistry(List.of(registration, registration)));
        assertEquals("duplicate tool registration: lookup_order", error.getMessage());
    }

    @Test
    void expectedToolExceptionBecomesTypedToolFailureAndStopsWithoutRetry() {
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicInteger toolCalls = new AtomicInteger();
        var cause = new IOException("offline fixture operation failed");
        var failure = new ToolExecutionException("order lookup unavailable", cause);
        var runtime = new AgentRuntime(context -> {
            modelCalls.incrementAndGet();
            return call("failed-call");
        }, registry(args -> {
            toolCalls.incrementAndGet();
            throw failure;
        }), 3);

        var failed = assertInstanceOf(AgentRuntime.ToolFailed.class, runtime.run("Question"));

        assertEquals(call("failed-call"), failed.call());
        assertSame(failure, failed.failure());
        assertSame(cause, failed.failure().getCause());
        assertEquals("order lookup unavailable", failed.failure().getMessage());
        assertEquals(1, modelCalls.get());
        assertEquals(1, toolCalls.get());
        assertEquals(1, failed.stepsTaken());
        assertTrue(failed.history().isEmpty());
        assertTrue(failed.steps().get(0).observation().isEmpty());
        assertEquals(List.of(AgentState.START, AgentState.MODEL, AgentState.TOOL, AgentState.STOP),
                failed.trace());
    }

    @Test
    void toolFailureKeepsEarlierObservationsWithoutInventingSuccessfulOutput() {
        AtomicInteger toolCalls = new AtomicInteger();
        var runtime = new AgentRuntime(context -> call("call-" + context.history().size()),
                registry(args -> {
                    if (toolCalls.incrementAndGet() == 1) {
                        return "first observation";
                    }
                    throw new ToolExecutionException("second operation failed");
                }), 4);

        var failed = assertInstanceOf(AgentRuntime.ToolFailed.class, runtime.run("Question"));

        assertEquals("call-1", failed.call().callId());
        assertEquals(2, failed.stepsTaken());
        assertEquals(2, toolCalls.get());
        assertEquals(List.of(new AgentContext.Exchange(call("call-0"),
                new ToolResult("call-0", "first observation"))), failed.history());
        assertEquals("first observation", failed.steps().get(0).observation().orElseThrow().output());
        assertTrue(failed.steps().get(1).observation().isEmpty());
    }

    @Test
    void maxStepsOnePreventsAnyToolExecution() {
        AtomicInteger toolCalls = new AtomicInteger();
        var runtime = new AgentRuntime(context -> call("never-executed"), registry(args -> {
            toolCalls.incrementAndGet();
            return "must not run";
        }), 1);

        var stopped = assertInstanceOf(AgentRuntime.Stopped.class, runtime.run("Question"));

        assertEquals(AgentRuntime.StopReason.MAX_STEPS, stopped.reason());
        assertEquals(1, stopped.stepsTaken());
        assertEquals(0, toolCalls.get());
        assertTrue(stopped.history().isEmpty());
    }

    @Test
    void finalAnswerOnTheLastAllowedStepIsStillSuccess() {
        var runtime = new AgentRuntime(new ReplayAgentModel(), registry(new ReplayOrderTool()), 2);

        var completed = assertInstanceOf(AgentRuntime.Completed.class, runtime.run("Question"));

        assertEquals(2, completed.stepsTaken());
        assertEquals("Replay answer: Order ORD-001: SHIPPED", completed.answer().text());
    }

    @Test
    void nonPositiveMaxStepsIsRejectedAtConstruction() {
        for (int maxSteps : List.of(0, -1)) {
            var error = assertThrows(IllegalArgumentException.class, () -> new AgentRuntime(
                    new ReplayAgentModel(), registry(new ReplayOrderTool()), maxSteps));
            assertEquals("maxSteps must be positive", error.getMessage());
        }
    }

    @Test
    void stepRejectsAnObservationWithTheWrongCallId() {
        var error = assertThrows(IllegalArgumentException.class, () -> new AgentStep(1,
                call("original"), Optional.of(new ToolResult("wrong", "SHIPPED"))));
        assertEquals("observation must match the step's tool callId", error.getMessage());
    }

    @Test
    void returnedTraceAndStepsAreImmutable() {
        var completed = assertInstanceOf(AgentRuntime.Completed.class,
                new AgentRuntime(new ReplayAgentModel(), registry(new ReplayOrderTool()), 2).run("Question"));

        assertThrows(UnsupportedOperationException.class, () -> completed.steps().clear());
        assertThrows(UnsupportedOperationException.class, () -> completed.trace().clear());
        assertThrows(UnsupportedOperationException.class, () -> completed.history().clear());
    }

    private static ToolRegistry registry(ToolHandler handler) {
        return new ToolRegistry(List.of(new ToolRegistry.Registration(ReplayOrderTool.DEFINITION, handler)));
    }

    private static AgentDecision.ToolCall call(String callId) {
        return new AgentDecision.ToolCall(callId, "lookup_order", Map.of("orderId", "ORD-001"));
    }

    private static void assertInvalidArguments(Map<String, String> arguments, String expectedDetail) {
        AtomicInteger toolCalls = new AtomicInteger();
        AtomicInteger modelCalls = new AtomicInteger();
        var runtime = new AgentRuntime(context -> {
            modelCalls.incrementAndGet();
            return new AgentDecision.ToolCall("bad-args", "lookup_order", arguments);
        }, registry(args -> {
            toolCalls.incrementAndGet();
            return "must not run";
        }), 3);

        var stopped = assertInstanceOf(AgentRuntime.Stopped.class, runtime.run("Question"));

        assertEquals(AgentRuntime.StopReason.INVALID_ARGUMENTS, stopped.reason());
        assertTrue(stopped.detail().contains(expectedDetail), stopped.detail());
        assertEquals(0, toolCalls.get());
        assertEquals(1, modelCalls.get());
        assertTrue(stopped.history().isEmpty());
        assertEquals(AgentState.STOP, stopped.trace().get(stopped.trace().size() - 1));
    }
}
