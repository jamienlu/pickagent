package io.github.jamielu.assistant;

import io.github.jamielu.assistant.application.AssistantModelException;
import io.github.jamielu.assistant.application.ChatClientAssistantService;
import io.github.jamielu.assistant.config.AssistantStreamProperties;
import io.github.jamielu.assistant.integration.springai.SpringAiChatResponseMapper;
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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证 SSE 流的消息、惰性、超时、错误和取消语义。 */
class AssistantStreamingTest {
    private static final String SYSTEM_PROMPT = "Answer accurately and concisely.";
    private static final Duration SIGNAL_TIMEOUT = Duration.ofSeconds(30);

    private DeterministicChatModel model;
    private ChatClientAssistantService service;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        model = new DeterministicChatModel();
        var chatClient = ChatClient.builder(model)
                .defaultSystem(SYSTEM_PROMPT)
                .build();
        service = new ChatClientAssistantService(
                chatClient,
                new SpringAiChatResponseMapper(),
                new AssistantStreamProperties(SIGNAL_TIMEOUT));
        client = WebTestClient.bindToController(new AssistantController(service))
                .controllerAdvice(new AssistantExceptionHandler())
                .build();
    }

    /** 验证 SSE 按序返回三个分片，并向模型传递统一系统消息和原样用户消息。 */
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

    /** 验证流式模型故障保持为错误信号，并在应用边界保留原始原因。 */
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

    /** 验证调用方取消订阅后，取消信号继续传播到底层模型流。 */
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

    /** 验证仅创建流不会调用或订阅模型，首次订阅才执行一次模型调用。 */
    @Test
    void streamingPipelineDoesNotSubscribeBeforeItsCaller() {
        AtomicInteger subscriptions = new AtomicInteger();
        model.streamingContent(Flux.just("lazy")
                .doOnSubscribe(ignored -> subscriptions.incrementAndGet()));

        Flux<String> result = service.stream("do not subscribe internally");

        assertEquals(0, model.streams());
        assertEquals(0, subscriptions.get());
        StepVerifier.create(result)
                .expectNext("lazy")
                .verifyComplete();
        assertEquals(1, subscriptions.get());
        assertEquals(1, model.streams());
    }

    /** 验证首个分片超过逐信号时限时，以虚拟时间产生模型超时错误。 */
    @Test
    void firstFragmentTimeoutIsMappedWithoutWaitingInRealTime() {
        model.streamingContent(Flux.never());

        StepVerifier.withVirtualTime(() -> service.stream("first fragment timeout"))
                .expectSubscription()
                .expectNoEvent(SIGNAL_TIMEOUT.minusMillis(1))
                .thenAwait(Duration.ofMillis(1))
                .expectErrorSatisfies(AssistantStreamingTest::assertTimeoutFailure)
                .verify();

        assertEquals(1, model.streams());
    }

    /** 验证每个分片都会重置计时器，相邻分片超时同样映射为模型错误。 */
    @Test
    void timeoutRestartsAfterEachFragment() {
        model.streamingContent(Flux.defer(() -> Flux.concat(
                Flux.just("first"),
                Mono.delay(SIGNAL_TIMEOUT.plusSeconds(1)).map(ignored -> "second"))));

        StepVerifier.withVirtualTime(() -> service.stream("inter-signal timeout"))
                .expectSubscription()
                .expectNext("first")
                .expectNoEvent(SIGNAL_TIMEOUT.minusMillis(1))
                .thenAwait(Duration.ofMillis(1))
                .expectErrorSatisfies(AssistantStreamingTest::assertTimeoutFailure)
                .verify();

        assertEquals(1, model.streams());
    }

    private static void assertTimeoutFailure(Throwable failure) {
        assertTrue(failure instanceof AssistantModelException);
        assertTrue(failure.getCause() instanceof TimeoutException);
    }
}
