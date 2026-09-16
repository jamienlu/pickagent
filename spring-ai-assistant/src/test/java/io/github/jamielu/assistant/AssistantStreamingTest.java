package io.github.jamielu.assistant;

import io.github.jamielu.assistant.application.AssistantModelException;
import io.github.jamielu.assistant.application.ChatClientAssistantService;
import io.github.jamielu.assistant.support.DeterministicChatModel;
import io.github.jamielu.assistant.web.AssistantController;
import io.github.jamielu.assistant.web.AssistantExceptionHandler;
import io.github.jamielu.assistant.web.ChatRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantStreamingTest {
    private static final String SYSTEM_PROMPT = "Answer accurately and concisely.";

    private DeterministicChatModel model;
    private ChatClientAssistantService service;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        model = new DeterministicChatModel();
        var chatClient = ChatClient.builder(model)
                .defaultSystem(SYSTEM_PROMPT)
                .build();
        service = new ChatClientAssistantService(chatClient);
        client = WebTestClient.bindToController(new AssistantController(service))
                .controllerAdvice(new AssistantExceptionHandler())
                .build();
    }

    @Test
    void streamingPromptContainsSameSystemAndExactUserMessagesInOrder() {
        model.streamingContent(Flux.just("first", "second", "third"));

        var response = client.post()
                .uri("/api/assistant/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(new ChatRequest("stream exactly"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(String.class);

        StepVerifier.create(response.getResponseBody())
                .expectNext("first", "second", "third")
                .verifyComplete();
        assertEquals(SYSTEM_PROMPT, model.lastSystemMessage().getText());
        assertEquals("stream exactly", model.lastUserMessage().getText());
        assertEquals(1, model.streams());
    }

    @Test
    void streamingModelFailureRemainsAnErrorSignal() {
        model.streamingContent(Flux.error(new IllegalStateException("stream failed")));

        StepVerifier.create(service.stream("fail as a signal"))
                .expectErrorSatisfies(failure -> {
                    assertTrue(failure instanceof AssistantModelException);
                    assertTrue(failure.getCause() instanceof IllegalStateException);
                })
                .verify();
    }

    @Test
    void cancellationPropagatesToTheUnderlyingModelFlux() {
        AtomicBoolean cancelled = new AtomicBoolean();
        model.streamingContent(Flux.concat(Flux.just("first"), Flux.never())
                .doOnCancel(() -> cancelled.set(true)));

        StepVerifier.create(service.stream("cancel after first"))
                .expectNext("first")
                .thenCancel()
                .verify();

        assertTrue(cancelled.get());
    }

    @Test
    void streamingPipelineDoesNotSubscribeBeforeItsCaller() {
        AtomicInteger subscriptions = new AtomicInteger();
        model.streamingContent(Flux.just("lazy")
                .doOnSubscribe(ignored -> subscriptions.incrementAndGet()));

        Flux<String> result = service.stream("do not subscribe internally");

        assertEquals(0, subscriptions.get());
        StepVerifier.create(result)
                .expectNext("lazy")
                .verifyComplete();
        assertEquals(1, subscriptions.get());
    }
}
