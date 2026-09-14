package io.github.jamielu.assistant;

import io.github.jamielu.assistant.application.ChatClientAssistantService;
import io.github.jamielu.assistant.support.DeterministicChatModel;
import io.github.jamielu.assistant.web.AssistantController;
import io.github.jamielu.assistant.web.AssistantExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssistantHttpTest {
    private DeterministicChatModel model;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        model = new DeterministicChatModel();
        var service = new ChatClientAssistantService(ChatClient.builder(model));
        var controller = new AssistantController(service);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AssistantExceptionHandler())
                .build();
    }

    @Test
    void validRequestTraversesControllerServiceAndChatClient() throws Exception {
        model.synchronousContent("A deterministic answer");

        mvc.perform(post("/api/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Explain virtual threads\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").value("A deterministic answer"));

        assertEquals("Explain virtual threads", model.lastUserContent());
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
