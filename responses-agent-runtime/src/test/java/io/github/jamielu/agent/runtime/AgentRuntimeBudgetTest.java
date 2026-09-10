package io.github.jamielu.agent.runtime;

import io.github.jamielu.agent.api.AgentDecision;
import io.github.jamielu.agent.api.ToolDefinition;
import io.github.jamielu.agent.reliability.FailureKind;
import io.github.jamielu.agent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class AgentRuntimeBudgetTest {
    private static final ToolDefinition TOOL = new ToolDefinition(
            "lookup", "测试工具", Set.of("id"));

    // 场景：模型在最后一次允许调用中仍请求工具；行为：运行时判断结果无人消费；预期：工具副作用前停止。
    @Test
    void modelCallLimitStopsBeforeUnconsumableTool() {
        AtomicInteger executions = new AtomicInteger();
        AgentRuntime runtime = new AgentRuntime(
                context -> call("call-1"), registry(executions),
                new RunBudget(1, 1, Duration.ZERO));

        AgentRuntime.Stopped stopped = assertInstanceOf(
                AgentRuntime.Stopped.class, runtime.run("question"));

        assertEquals(AgentRuntime.StopReason.MODEL_CALL_LIMIT, stopped.reason());
        assertEquals(1, stopped.usage().modelCalls());
        assertEquals(0, stopped.usage().toolCalls());
        assertEquals(0, executions.get());
    }

    // 场景：应用工具预算为零；行为：模型提出合法工具调用；预期：优先以工具预算原因停止且不执行处理器。
    @Test
    void toolCallLimitStopsBeforeSideEffect() {
        AtomicInteger executions = new AtomicInteger();
        AgentRuntime runtime = new AgentRuntime(
                context -> call("call-1"), registry(executions),
                new RunBudget(2, 0, Duration.ZERO));

        AgentRuntime.Stopped stopped = assertInstanceOf(
                AgentRuntime.Stopped.class, runtime.run("question"));

        assertEquals(AgentRuntime.StopReason.TOOL_CALL_LIMIT, stopped.reason());
        assertEquals(1, stopped.usage().modelCalls());
        assertEquals(0, stopped.usage().toolCalls());
        assertEquals(0, executions.get());
    }

    // 场景：首个模型调用前已到截止时间；行为：运行时预检；预期：零调用终止并记录确定性耗时。
    @Test
    void deadlineStopsBeforeFirstModelCall() {
        SequenceClock clock = new SequenceClock(0, 1_000_000_000L, 1_000_000_000L);
        AtomicInteger modelCalls = new AtomicInteger();
        AgentRuntime runtime = AgentRuntime.withModelFactory(
                () -> context -> {
                    modelCalls.incrementAndGet();
                    return new AgentDecision.FinalAnswer("unused");
                }, registry(new AtomicInteger()),
                new RunBudget(2, 1, Duration.ofSeconds(1)), clock);

        AgentRuntime.Stopped stopped = assertInstanceOf(
                AgentRuntime.Stopped.class, runtime.run("question"));

        assertEquals(AgentRuntime.StopReason.DEADLINE_EXCEEDED, stopped.reason());
        assertEquals(0, modelCalls.get());
        assertEquals(new RunUsage(0, 0, Duration.ofSeconds(1)), stopped.usage());
    }

    // 场景：模型调用期间耗尽总时限；行为：模型返回工具调用后再次预检；预期：工具副作用不会发生。
    @Test
    void deadlineStopsBeforeToolSideEffect() {
        AtomicLong now = new AtomicLong();
        AtomicInteger executions = new AtomicInteger();
        AgentRuntime runtime = AgentRuntime.withModelFactory(
                () -> context -> {
                    now.set(2_000_000_000L);
                    return call("call-1");
                }, registry(executions),
                new RunBudget(2, 1, Duration.ofSeconds(1)), now::get);

        AgentRuntime.Stopped stopped = assertInstanceOf(
                AgentRuntime.Stopped.class, runtime.run("question"));

        assertEquals(AgentRuntime.StopReason.DEADLINE_EXCEEDED, stopped.reason());
        assertEquals(new RunUsage(1, 0, Duration.ofSeconds(2)), stopped.usage());
        assertEquals(0, executions.get());
    }

    // 场景：模型端口抛出已分类异常；行为：运行时归一化终态；预期：保留同一异常并计入一次模型尝试。
    @Test
    void typedModelFailureBecomesAuditableTerminalResult() {
        var failure = new ModelExecutionException(
                FailureKind.TIMEOUT, "model timed out", new RuntimeException("socket"));
        AgentRuntime runtime = new AgentRuntime(context -> {
            throw failure;
        }, registry(new AtomicInteger()), new RunBudget(2, 1, Duration.ZERO));

        AgentRuntime.ModelFailed failed = assertInstanceOf(
                AgentRuntime.ModelFailed.class, runtime.run("question"));

        assertSame(failure, failed.failure());
        assertEquals(FailureKind.TIMEOUT, failed.failure().kind());
        assertEquals(1, failed.usage().modelCalls());
        assertEquals(0, failed.usage().toolCalls());
    }

    // 场景：一次工具调用后模型给出答案；行为：运行正常完成；预期：终态准确记录两次模型和一次工具调用。
    @Test
    void completedRunReportsAllResourceUsage() {
        AtomicInteger executions = new AtomicInteger();
        AgentRuntime runtime = AgentRuntime.withModelFactory(
                () -> context -> context.history().isEmpty()
                        ? call("call-1")
                        : new AgentDecision.FinalAnswer("done"),
                registry(executions), new RunBudget(2, 1, Duration.ZERO));

        AgentRuntime.Completed completed = assertInstanceOf(
                AgentRuntime.Completed.class, runtime.run("question"));

        assertEquals(2, completed.usage().modelCalls());
        assertEquals(1, completed.usage().toolCalls());
        assertEquals(1, executions.get());
    }

    // 场景：注册表返回三种稳定拒绝原因；行为：转换为运行停止原因；预期：分类逐一保持语义。
    @Test
    void mapsEveryRegistryRejectionReason() {
        assertEquals(AgentRuntime.StopReason.UNKNOWN_TOOL,
                AgentRuntime.mapRejection(ToolRegistry.Rejection.UNKNOWN_TOOL));
        assertEquals(AgentRuntime.StopReason.INVALID_ARGUMENTS,
                AgentRuntime.mapRejection(ToolRegistry.Rejection.INVALID_ARGUMENTS));
        assertEquals(AgentRuntime.StopReason.DUPLICATE_CALL_ID,
                AgentRuntime.mapRejection(ToolRegistry.Rejection.DUPLICATE_CALL_ID));
    }

    private static AgentDecision.ToolCall call(String callId) {
        return new AgentDecision.ToolCall(callId, "lookup", Map.of("id", "1"));
    }

    private static ToolRegistry registry(AtomicInteger executions) {
        return new ToolRegistry(List.of(new ToolRegistry.Registration(
                TOOL, arguments -> {
                    executions.incrementAndGet();
                    return "ok";
                })));
    }

    private static final class SequenceClock implements NanoClock {
        private final long[] values;
        private int index;

        private SequenceClock(long... values) {
            this.values = values;
        }

        @Override
        public long nanoTime() {
            int current = Math.min(index, values.length - 1);
            index++;
            return values[current];
        }
    }
}
