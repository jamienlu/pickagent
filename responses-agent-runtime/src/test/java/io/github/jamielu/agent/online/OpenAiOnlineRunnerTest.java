package io.github.jamielu.agent.online;

import io.github.jamielu.agent.api.AgentDecision;
import io.github.jamielu.agent.openai.OpenAiResponseOptions;
import io.github.jamielu.agent.runtime.AgentRuntime;
import io.github.jamielu.agent.runtime.RunBudget;
import io.github.jamielu.agent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiOnlineRunnerTest {
    // 场景：在线组合执行成功；行为：运行一次模型会话；预期：返回核心终态并始终关闭底层资源。
    @Test
    void runsAndClosesIndependentModelSession() {
        AtomicBoolean closed = new AtomicBoolean();
        OpenAiOnlineRunner runner = new OpenAiOnlineRunner(config ->
                new OpenAiOnlineRunner.ModelSession(
                        context -> new AgentDecision.FinalAnswer("done"),
                        () -> closed.set(true)));

        AgentRuntime.Completed completed = assertInstanceOf(
                AgentRuntime.Completed.class,
                runner.run(config(), "question", new ToolRegistry(List.of())));

        assertEquals("done", completed.answer().text());
        assertTrue(closed.get());
    }

    // 场景：会话资源关闭失败；行为：离开运行作用域；预期：保留关闭原因并转换为明确运行异常。
    @Test
    void reportsResourceCloseFailure() {
        Exception cause = new Exception("close failed");
        OpenAiOnlineRunner runner = new OpenAiOnlineRunner(config ->
                new OpenAiOnlineRunner.ModelSession(
                        context -> new AgentDecision.FinalAnswer("done"),
                        () -> { throw cause; }));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> runner.run(config(), "question", new ToolRegistry(List.of())));

        assertEquals("failed to close online model session", failure.getMessage());
        assertEquals(cause, failure.getCause());
    }

    // 场景：组合依赖返回空会话；行为：开始运行；预期：在模型调用前快速失败。
    @Test
    void rejectsNullSessionFromFactory() {
        OpenAiOnlineRunner runner = new OpenAiOnlineRunner(config -> null);

        assertThrows(NullPointerException.class,
                () -> runner.run(config(), "question", new ToolRegistry(List.of())));
    }

    // 场景：会话资源字段为空；行为：构造会话；预期：分别拒绝空模型和空资源。
    @Test
    void modelSessionRejectsNullFields() {
        assertThrows(NullPointerException.class,
                () -> new OpenAiOnlineRunner.ModelSession(null, () -> { }));
        assertThrows(NullPointerException.class,
                () -> new OpenAiOnlineRunner.ModelSession(
                        context -> new AgentDecision.FinalAnswer("done"), null));
    }

    // 场景：调用生产组合工厂；行为：创建组合服务；预期：仅完成依赖装配且不会触发网络请求。
    @Test
    void createsProductionCompositionWithoutNetworkCall() {
        assertNotNull(OpenAiOnlineRunner.production());
        try (OpenAiOnlineRunner.ModelSession session =
                     OpenAiOnlineRunner.productionSession(config())) {
            assertNotNull(session.model());
        }
    }

    private static OpenAiOnlineConfig config() {
        return new OpenAiOnlineConfig(
                "key", "model", Optional.empty(), Optional.empty(),
                Duration.ofSeconds(1), 0,
                OpenAiResponseOptions.defaults(),
                new RunBudget(1, 0, Duration.ZERO));
    }
}
