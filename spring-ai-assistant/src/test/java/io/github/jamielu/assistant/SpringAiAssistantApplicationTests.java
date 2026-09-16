package io.github.jamielu.assistant;

import io.github.jamielu.assistant.application.AssistantService;
import io.github.jamielu.assistant.support.DeterministicChatModel;
import io.github.jamielu.assistant.support.OfflineChatModelConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.ai.model.chat=none",
        "spring.ai.model.embedding=none",
        "spring.ai.model.image=none",
        "spring.ai.model.moderation=none",
        "spring.ai.model.audio.speech=none",
        "spring.ai.model.audio.transcription=none",
        "spring.main.web-application-type=none"
})
@Import(OfflineChatModelConfiguration.class)
class SpringAiAssistantApplicationTests {
    @Autowired
    private AssistantService assistantService;

    @Autowired
    private DeterministicChatModel chatModel;

    @Test
    void contextAndChatClientWorkWithoutAnOpenAiApiKey() {
        chatModel.synchronousContent("offline context answer");

        String answer = assistantService.chat("offline context request");

        assertEquals("offline context answer", answer);
        assertEquals("You are a concise and accurate assistant.", chatModel.lastSystemMessage().getText());
        assertEquals("offline context request", chatModel.lastUserMessage().getText());
        assertEquals(1, chatModel.calls());
    }
}
