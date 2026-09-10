package io.github.jamielu.agent.runtime;

import io.github.jamielu.agent.api.AgentContext;
import io.github.jamielu.agent.api.AgentDecision;
import io.github.jamielu.agent.api.AgentState;
import io.github.jamielu.agent.api.AgentStep;
import io.github.jamielu.agent.api.ToolResult;
import io.github.jamielu.agent.example.support.ReplayAgentModel;
import io.github.jamielu.agent.example.support.ReplayOrderTool;
import io.github.jamielu.agent.tool.ToolExecutionException;
import io.github.jamielu.agent.tool.ToolHandler;
import io.github.jamielu.agent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AgentRuntimeTest {
    // 场景：工具调用、观察和最终回答完整保留上下文及原始调用标识；行为：执行对应代码路径；预期：相关业务断言全部成立。
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
        var repeated = assertInstanceOf(
                AgentRuntime.Completed.class, runtime.run("Find ORD-001"));
        assertEquals(completed.answer(), repeated.answer(), "Replay is repeatable across runs");
        assertEquals(completed.trace(), repeated.trace());
        assertEquals(completed.history(), repeated.history());
        assertEquals(completed.steps(), repeated.steps());
        assertEquals(completed.usage().modelCalls(), repeated.usage().modelCalls());
        assertEquals(completed.usage().toolCalls(), repeated.usage().toolCalls());
    }

    // 场景：模型直接回答时不执行任何工具；行为：执行对应代码路径；预期：相关业务断言全部成立。
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

    // 场景：未知工具在执行和下一次模型调用前停止；行为：执行对应代码路径；预期：相关业务断言全部成立。
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

    // 场景：缺少必填参数时在工具副作用前停止；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void missingArgumentStopsBeforeToolExecution() {
        assertInvalidArguments(Map.of(), "missing=[orderId], extra=[]");
    }

    // 场景：出现多余参数时在工具副作用前停止；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void extraArgumentStopsBeforeToolExecution() {
        assertInvalidArguments(Map.of("orderId", "ORD-001", "admin", "true"), "missing=[], extra=[admin]");
    }

    // 场景：参数为空白时在工具副作用前停止；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void blankArgumentStopsBeforeToolExecution() {
        assertInvalidArguments(Map.of("orderId", " "), "blank argument: orderId");
    }

    // 场景：同一运行重复调用标识时不重复执行工具；行为：执行对应代码路径；预期：相关业务断言全部成立。
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

    // 场景：模型调用预算耗尽时不执行无人消费结果的工具；行为：执行对应代码路径；预期：相关业务断言全部成立。
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

        assertEquals(AgentRuntime.StopReason.MODEL_CALL_LIMIT, stopped.reason());
        assertEquals("model call limit reached before tool execution: 2", stopped.detail());
        assertEquals(2, stopped.stepsTaken());
        assertEquals(2, stopped.steps().get(1).number());
        assertTrue(stopped.steps().get(1).observation().isEmpty());
        assertEquals(2, modelCalls.get());
        assertEquals(1, toolCalls.get());
        assertEquals(List.of(AgentState.START, AgentState.MODEL, AgentState.TOOL,
                AgentState.MODEL, AgentState.TOOL, AgentState.STOP), stopped.trace());
    }

    // 场景：未知模型编程错误原样暴露而不伪装成预期停止；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void modelProgrammingErrorIsNotDisguisedAsExpectedStop() {
        var bug = new IllegalStateException("model mapping bug");
        var runtime = new AgentRuntime(context -> {
            throw bug;
        }, registry(new ReplayOrderTool()), 3);

        assertSame(bug, assertThrows(IllegalStateException.class, () -> runtime.run("Question")));
    }

    // 场景：未知工具编程错误原样暴露而不伪装成校验失败；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void toolProgrammingErrorIsNotDisguisedAsValidationFailure() {
        var bug = new IllegalStateException("tool implementation bug");
        var runtime = new AgentRuntime(context -> call("call-bug"), registry(args -> {
            throw bug;
        }), 3);

        assertSame(bug, assertThrows(IllegalStateException.class, () -> runtime.run("Question")));
    }

    // 场景：工具交互拒绝调用与结果标识不一致；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void exchangeRejectsMismatchedCallId() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> new AgentContext.Exchange(call("original"), new ToolResult("wrong", "SHIPPED")));
        assertEquals("tool result callId must match original callId", error.getMessage());
    }

    // 场景：注册表拒绝同名工具重复注册；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void registryRejectsDuplicateToolRegistration() {
        var registration = new ToolRegistry.Registration(
                ReplayOrderTool.DEFINITION, new ReplayOrderTool());
        var error = assertThrows(IllegalArgumentException.class,
                () -> new ToolRegistry(List.of(registration, registration)));
        assertEquals("duplicate tool registration: lookup_order", error.getMessage());
    }

    // 场景：已分类工具异常转换为类型化终态且不自动重试；行为：执行对应代码路径；预期：相关业务断言全部成立。
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

    // 场景：后续工具失败保留先前观察且不伪造成功结果；行为：执行对应代码路径；预期：相关业务断言全部成立。
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

    // 场景：只有一次模型预算时禁止执行无法续接的工具；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void maxStepsOnePreventsAnyToolExecution() {
        AtomicInteger toolCalls = new AtomicInteger();
        var runtime = new AgentRuntime(context -> call("never-executed"), registry(args -> {
            toolCalls.incrementAndGet();
            return "must not run";
        }), 1);

        var stopped = assertInstanceOf(AgentRuntime.Stopped.class, runtime.run("Question"));

        assertEquals(AgentRuntime.StopReason.MODEL_CALL_LIMIT, stopped.reason());
        assertEquals(1, stopped.stepsTaken());
        assertEquals(0, toolCalls.get());
        assertTrue(stopped.history().isEmpty());
    }

    // 场景：最后一次允许的模型调用给出答案时仍成功完成；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void finalAnswerOnTheLastAllowedStepIsStillSuccess() {
        var runtime = new AgentRuntime(new ReplayAgentModel(), registry(new ReplayOrderTool()), 2);

        var completed = assertInstanceOf(AgentRuntime.Completed.class, runtime.run("Question"));

        assertEquals(2, completed.stepsTaken());
        assertEquals("Replay answer: Order ORD-001: SHIPPED", completed.answer().text());
    }

    // 场景：构造阶段拒绝非正模型步骤上限；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void nonPositiveMaxStepsIsRejectedAtConstruction() {
        for (int maxSteps : List.of(0, -1)) {
            var error = assertThrows(IllegalArgumentException.class, () -> new AgentRuntime(
                    new ReplayAgentModel(), registry(new ReplayOrderTool()), maxSteps));
            assertEquals("maxModelCalls must be positive", error.getMessage());
        }
    }

    // 场景：步骤拒绝关联到其他调用标识的观察；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void stepRejectsAnObservationWithTheWrongCallId() {
        var error = assertThrows(IllegalArgumentException.class, () -> new AgentStep(1,
                call("original"), Optional.of(new ToolResult("wrong", "SHIPPED"))));
        assertEquals("observation must match the step's tool callId", error.getMessage());
    }

    // 场景：返回的轨迹、步骤和历史快照不可修改；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void returnedTraceAndStepsAreImmutable() {
        var completed = assertInstanceOf(AgentRuntime.Completed.class,
                new AgentRuntime(new ReplayAgentModel(), registry(new ReplayOrderTool()), 2).run("Question"));

        assertThrows(UnsupportedOperationException.class, () -> completed.steps().clear());
        assertThrows(UnsupportedOperationException.class, () -> completed.trace().clear());
        assertThrows(UnsupportedOperationException.class, () -> completed.history().clear());
    }

    // 场景：模型工厂为每次运行创建独立会话实例；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void modelFactoryCreatesAnIndependentModelForEveryRun() {
        AtomicInteger factoryCalls = new AtomicInteger();
        var runtime = AgentRuntime.withModelFactory(() -> {
            int instance = factoryCalls.incrementAndGet();
            AtomicInteger decisions = new AtomicInteger();
            return context -> new AgentDecision.FinalAnswer(
                    "instance-" + instance + "-decision-" + decisions.incrementAndGet());
        }, registry(new ReplayOrderTool()), 1);

        var first = assertInstanceOf(AgentRuntime.Completed.class, runtime.run("first"));
        var second = assertInstanceOf(AgentRuntime.Completed.class, runtime.run("second"));

        assertEquals("instance-1-decision-1", first.answer().text());
        assertEquals("instance-2-decision-1", second.answer().text());
        assertEquals(2, factoryCalls.get());
    }

    // 场景：模型工厂返回空值时在运行边界失败；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void nullModelFromFactoryFailsAtRunBoundary() {
        var runtime = AgentRuntime.withModelFactory(
                () -> null, registry(new ReplayOrderTool()), 1);

        NullPointerException failure = assertThrows(
                NullPointerException.class, () -> runtime.run("Question"));

        assertEquals("modelFactory returned null", failure.getMessage());
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


