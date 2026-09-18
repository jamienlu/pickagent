package io.github.jamielu.assistant;

import io.github.jamielu.assistant.application.AssistantService;
import io.github.jamielu.assistant.config.AssistantStreamProperties;
import io.github.jamielu.assistant.support.DeterministicChatModel;
import io.github.jamielu.assistant.support.OfflineChatModelConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证完整 Spring 上下文可在无 OpenAI 密钥和无网络环境下运行。 */
@SpringBootTest(properties = {
        "spring.ai.model.chat=none",
        "spring.ai.model.embedding=none",
        "spring.ai.model.image=none",
        "spring.ai.model.moderation=none",
        "spring.ai.model.audio.speech=none",
        "spring.ai.model.audio.transcription=none",
        "ASSISTANT_STREAM_SIGNAL_TIMEOUT=750ms",
        "spring.main.web-application-type=none"
})
@Import(OfflineChatModelConfiguration.class)
class SpringAiAssistantApplicationTests {
    @Autowired
    private AssistantService assistantService;

    @Autowired
    private DeterministicChatModel chatModel;

    @Autowired
    private AssistantStreamProperties streamProperties;

    /** 验证完整上下文使用离线模型完成同步调用，不依赖 OpenAI API Key。 */
    @Test
    void contextAndChatClientWorkWithoutAnOpenAiApiKey() {
        chatModel.synchronousContent("offline context answer");

        var answer = assistantService.chat("offline context request");

        assertEquals("offline context answer", answer.message());
        assertEquals("You are a concise and accurate assistant.", chatModel.lastSystemMessage().getText());
        assertEquals("offline context request", chatModel.lastUserMessage().getText());
        assertEquals(1, chatModel.calls());
    }

    /** 验证环境变量风格属性能够覆盖默认逐信号超时。 */
    @Test
    void externalEnvironmentStylePropertyOverridesDefaultSignalTimeout() {
        assertEquals(Duration.ofMillis(750), streamProperties.signalTimeout());
    }
}
