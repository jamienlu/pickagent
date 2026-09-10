package io.github.jamielu.agent.live;

import io.github.jamielu.agent.online.OpenAiOnlineConfig;
import io.github.jamielu.agent.online.OpenAiOnlineRunner;
import io.github.jamielu.agent.runtime.AgentRuntime;
import io.github.jamielu.agent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OpenAiLiveSmokeIT {
    // 场景：操作者显式启用 live 配置档并提供密钥与模型；行为：发送最小 Responses 请求；预期：收到非空白最终文本。
    @Test
    void receivesMinimalFinalTextFromConfiguredOpenAiEndpoint() {
        Map<String, String> environment = System.getenv();
        assumeTrue(environment.containsKey("OPENAI_API_KEY")
                        && environment.containsKey("OPENAI_MODEL"),
                "live smoke requires OPENAI_API_KEY and OPENAI_MODEL");

        AgentRuntime.Completed completed = assertInstanceOf(
                AgentRuntime.Completed.class,
                OpenAiOnlineRunner.production().run(
                        OpenAiOnlineConfig.fromEnvironment(environment),
                        "Reply with exactly: LIVE_OK",
                        new ToolRegistry(List.of())));

        assertFalse(completed.answer().text().isBlank());
    }
}
