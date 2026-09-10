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
    // 场景：批次第二个调用无效时，完整预检必须阻止所有处理器执行；行为：执行对应代码路径；预期：相关业务断言全部成立。
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

    // 场景：批次调用标识重复时，完整预检必须阻止所有处理器执行；行为：执行对应代码路径；预期：相关业务断言全部成立。
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

    // 场景：已预检批次按调用方顺序执行并保留结果关联；行为：执行对应代码路径；预期：相关业务断言全部成立。
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

    // 场景：一个注册表准备的调用不能由另一个注册表执行；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void preparedCallCannotBeExecutedByAnotherRegistry() {
        ToolRegistry first = registry(new AtomicInteger(), new ArrayList<>());
        ToolRegistry second = registry(new AtomicInteger(), new ArrayList<>());
        ToolRegistry.PreparedCall prepared = first.prepare(call("call_1", "echo", "value"));

        assertEquals("call_1", prepared.call().callId());
        assertThrows(IllegalArgumentException.class, () -> second.execute(prepared));
    }

    // 场景：执行入口收到空的已准备调用；行为：进入注册表执行；预期：立即拒绝空引用。
    @Test
    void executeRejectsNullPreparedCall() {
        assertThrows(NullPointerException.class,
                () -> registry(new AtomicInteger(), new ArrayList<>()).execute(
                        (ToolRegistry.PreparedCall) null));
    }

    // 场景：批量预检收到空批次；行为：准备全部调用；预期：拒绝没有任何工具调用的批次。
    @Test
    void prepareAllRejectsEmptyBatch() {
        assertThrows(IllegalArgumentException.class,
                () -> registry(new AtomicInteger(), new ArrayList<>()).prepareAll(List.of()));
    }

    // 场景：工具处理器违反契约并返回空值；行为：注册表执行已验证调用；预期：明确暴露处理器编程错误。
    @Test
    void executeRejectsNullHandlerOutput() {
        ToolDefinition definition = new ToolDefinition(
                "echo", "Echo a value", Set.of("value"));
        ToolRegistry registry = new ToolRegistry(List.of(
                new ToolRegistry.Registration(definition, arguments -> null)));

        assertThrows(NullPointerException.class,
                () -> registry.execute(call("call-1", "echo", "value")));
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
