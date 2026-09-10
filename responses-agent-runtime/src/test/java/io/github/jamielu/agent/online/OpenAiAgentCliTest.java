package io.github.jamielu.agent.online;

import io.github.jamielu.agent.api.AgentDecision;
import io.github.jamielu.agent.api.AgentState;
import io.github.jamielu.agent.reliability.FailureKind;
import io.github.jamielu.agent.runtime.AgentRuntime;
import io.github.jamielu.agent.runtime.ModelExecutionException;
import io.github.jamielu.agent.runtime.RunUsage;
import io.github.jamielu.agent.tool.ToolExecutionException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiAgentCliTest {
    private static final Map<String, String> ENVIRONMENT = Map.of(
            "OPENAI_API_KEY", "test-key", "OPENAI_MODEL", "test-model");
    private static final RunUsage NO_USAGE = new RunUsage(0, 0, Duration.ZERO);

    // 场景：命令参数数量不正确或输入为空白；行为：执行命令解析；预期：返回用法错误且不调用在线用例。
    @Test
    void rejectsInvalidArgumentsBeforeOnlineComposition() {
        Output output = new Output();
        OpenAiAgentCli.OnlineCommand unused = (config, input, tools) -> {
            throw new AssertionError("非法参数不得调用在线用例");
        };

        assertEquals(2, OpenAiAgentCli.execute(new String[0], Map.of(),
                output.out(), output.err(), unused));
        assertEquals(2, OpenAiAgentCli.execute(new String[]{"  "}, Map.of(),
                output.out(), output.err(), unused));
        assertTrue(output.errorText().contains("用法"));
    }

    // 场景：模型返回最终回答；行为：映射命令终态；预期：输出回答并返回成功退出码。
    @Test
    void printsCompletedAnswer() {
        Output output = new Output();
        AgentRuntime.Completed completed = new AgentRuntime.Completed(
                new AgentDecision.FinalAnswer("完成"),
                List.of(AgentState.START, AgentState.MODEL, AgentState.FINAL, AgentState.STOP),
                List.of(), List.of(), NO_USAGE);

        int exitCode = OpenAiAgentCli.execute(new String[]{"你好"}, ENVIRONMENT,
                output.out(), output.err(), (config, input, tools) -> completed);

        assertEquals(0, exitCode);
        assertEquals("完成" + System.lineSeparator(), output.outputText());
        assertEquals("", output.errorText());
    }

    // 场景：模型请求失败；行为：映射命令终态；预期：只输出稳定失败分类并返回模型错误码。
    @Test
    void mapsModelFailure() {
        Output output = new Output();
        ModelExecutionException failure = new ModelExecutionException(
                FailureKind.TIMEOUT, "模型超时", new IllegalStateException("底层失败"));
        AgentRuntime.ModelFailed result = new AgentRuntime.ModelFailed(failure,
                List.of(AgentState.START, AgentState.MODEL, AgentState.STOP),
                List.of(), List.of(), NO_USAGE);

        int exitCode = OpenAiAgentCli.execute(new String[]{"你好"}, ENVIRONMENT,
                output.out(), output.err(), (config, input, tools) -> result);

        assertEquals(4, exitCode);
        assertTrue(output.errorText().contains("TIMEOUT"));
    }

    // 场景：运行预算或安全边界停止；行为：映射命令终态；预期：输出停止分类并返回停止错误码。
    @Test
    void mapsStoppedResult() {
        Output output = new Output();
        AgentRuntime.Stopped result = new AgentRuntime.Stopped(
                AgentRuntime.StopReason.MODEL_CALL_LIMIT, "预算耗尽",
                List.of(AgentState.START, AgentState.STOP), List.of(), List.of(), NO_USAGE);

        int exitCode = OpenAiAgentCli.execute(new String[]{"你好"}, ENVIRONMENT,
                output.out(), output.err(), (config, input, tools) -> result);

        assertEquals(3, exitCode);
        assertTrue(output.errorText().contains("MODEL_CALL_LIMIT"));
    }

    // 场景：工具处理器返回预期失败；行为：映射命令终态；预期：返回工具错误码且不输出敏感失败文本。
    @Test
    void mapsToolFailure() {
        Output output = new Output();
        AgentDecision.ToolCall call = new AgentDecision.ToolCall(
                "call-1", "lookup", Map.of());
        AgentRuntime.ToolFailed result = new AgentRuntime.ToolFailed(
                call, new ToolExecutionException("工具失败"),
                List.of(AgentState.START, AgentState.MODEL, AgentState.TOOL, AgentState.STOP),
                List.of(), List.of(), NO_USAGE);

        int exitCode = OpenAiAgentCli.execute(new String[]{"你好"}, ENVIRONMENT,
                output.out(), output.err(), (config, input, tools) -> result);

        assertEquals(5, exitCode);
        assertEquals("工具执行失败" + System.lineSeparator(), output.errorText());
    }

    // 场景：在线必需配置缺失；行为：在调用用例前构建配置；预期：返回配置错误且不会访问在线边界。
    @Test
    void mapsConfigurationFailure() {
        Output output = new Output();
        OpenAiAgentCli.OnlineCommand unused = (config, input, tools) -> {
            throw new AssertionError("配置失败不得调用在线用例");
        };

        int exitCode = OpenAiAgentCli.execute(new String[]{"你好"}, Map.of(),
                output.out(), output.err(), unused);

        assertEquals(2, exitCode);
        assertTrue(output.errorText().contains("OPENAI_API_KEY"));
    }

    // 场景：命令执行成功或失败；行为：应用顶层退出策略；预期：零退出码正常返回，非零退出码转换为稳定异常。
    @Test
    void enforcesTopLevelExitPolicy() {
        OpenAiAgentCli.failOnNonZero(0);

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> OpenAiAgentCli.failOnNonZero(3));
        assertEquals("online CLI failed with exit code 3", failure.getMessage());
    }

    // 场景：直接调用在线主入口但未提供参数；行为：执行生产装配前的参数校验；预期：无需网络即可返回非零异常。
    @Test
    void mainRejectsMissingInputWithoutNetwork() {
        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> OpenAiAgentCli.main(new String[0]));

        assertEquals("online CLI failed with exit code 2", failure.getMessage());
    }

    /** 捕获命令标准输出与标准错误。 */
    private static final class Output {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final ByteArrayOutputStream error = new ByteArrayOutputStream();

        private PrintStream out() {
            return new PrintStream(output, true, StandardCharsets.UTF_8);
        }

        private PrintStream err() {
            return new PrintStream(error, true, StandardCharsets.UTF_8);
        }

        private String outputText() {
            return output.toString(StandardCharsets.UTF_8);
        }

        private String errorText() {
            return error.toString(StandardCharsets.UTF_8);
        }
    }
}
