package io.github.jamielu.assistant;

import io.github.jamielu.assistant.application.ChatClientAssistantService;
import io.github.jamielu.assistant.integration.springai.SpringAiChatResponseMapper;
import io.github.jamielu.assistant.support.DeterministicChatModel;
import io.github.jamielu.assistant.web.AssistantController;
import io.github.jamielu.assistant.web.AssistantExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssistantHttpTest {
    private static final String SYSTEM_PROMPT = "Answer accurately and concisely.";

    private DeterministicChatModel model;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        model = new DeterministicChatModel();
        var chatClient = ChatClient.builder(model)
                .defaultSystem(SYSTEM_PROMPT)
                .build();
        var service = new ChatClientAssistantService(chatClient, new SpringAiChatResponseMapper());
        var controller = new AssistantController(service);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AssistantExceptionHandler())
                .build();
    }

    @Test
    void synchronousPromptContainsConfiguredSystemAndExactUserMessages() throws Exception {
        model.synchronousContent("A deterministic answer");

        mvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Explain virtual threads\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").value("A deterministic answer"))
                .andExpect(jsonPath("$.message").doesNotExist());

        assertEquals(SYSTEM_PROMPT, model.lastSystemMessage().getText());
        assertEquals("Explain virtual threads", model.lastUserMessage().getText());
        assertEquals(1, model.calls());
    }

    @Test
    void completeMetadataIsMappedFromTheSameSynchronousResponse() throws Exception {
        var metadata = ChatResponseMetadata.builder()
                .id("response-123")
                .model("offline-model")
                .usage(new DefaultUsage(12, 7, 19))
                .build();
        model.synchronousResponse(new ChatResponse(
                List.of(new Generation(new AssistantMessage("metadata answer"))),
                metadata));

        mvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"include metadata\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("metadata answer"))
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.responseId").value("response-123"))
                .andExpect(jsonPath("$.model").value("offline-model"))
                .andExpect(jsonPath("$.usage.promptTokens").value(12))
                .andExpect(jsonPath("$.usage.completionTokens").value(7))
                .andExpect(jsonPath("$.usage.totalTokens").value(19));

        assertEquals(1, model.calls());
    }

    @Test
    void missingMetadataAndUsageRemainUnknownInsteadOfSyntheticZero() throws Exception {
        model.synchronousContent("answer without metadata");

        mvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"metadata may be absent\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("answer without metadata"))
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.responseId").value(nullValue()))
                .andExpect(jsonPath("$.model").value(nullValue()))
                .andExpect(jsonPath("$.usage").value(nullValue()));

        assertEquals(1, model.calls());
    }

    @Test
    void blankInputMapsToBadRequestWithoutInvokingTheModel() throws Exception {
        mvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("message must not be blank"));

        assertEquals(0, model.calls());
    }

    @Test
    void modelFailureMapsToBadGateway() throws Exception {
        model.synchronousFailure(new IllegalStateException("offline provider failure"));

        mvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("MODEL_FAILURE"))
                .andExpect(jsonPath("$.message").value("assistant model invocation failed"));

        assertEquals(1, model.calls());
    }
}
