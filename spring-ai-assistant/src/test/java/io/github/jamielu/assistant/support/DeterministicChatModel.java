package io.github.jamielu.assistant.support;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Deterministic, network-free ChatModel used by every test. */
public final class DeterministicChatModel implements ChatModel {
    private final AtomicReference<SystemMessage> lastSystemMessage = new AtomicReference<>();
    private final AtomicReference<UserMessage> lastUserMessage = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger streams = new AtomicInteger();
    private volatile ChatResponse synchronousResponse = response("fixed answer");
    private volatile RuntimeException synchronousFailure;
    private volatile Flux<String> streamingContent = Flux.just("fixed", " ", "stream");

    @Override
    public ChatResponse call(Prompt prompt) {
        calls.incrementAndGet();
        capture(prompt);
        if (synchronousFailure != null) {
            throw synchronousFailure;
        }
        return synchronousResponse;
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        streams.incrementAndGet();
        capture(prompt);
        return Flux.defer(() -> streamingContent.map(DeterministicChatModel::response));
    }

    /** Sets the complete response returned by {@link #call(Prompt)}. */
    public void synchronousContent(String content) {
        this.synchronousResponse = response(Objects.requireNonNull(content, "content"));
        this.synchronousFailure = null;
    }

    /** Sets the complete response, including deterministic metadata and usage. */
    public void synchronousResponse(ChatResponse response) {
        this.synchronousResponse = Objects.requireNonNull(response, "response");
        this.synchronousFailure = null;
    }

    /** Makes synchronous invocation fail deterministically. */
    public void synchronousFailure(RuntimeException failure) {
        this.synchronousFailure = Objects.requireNonNull(failure, "failure");
    }

    /** Sets an ordered, possibly failing or cancellable streaming fixture. */
    public void streamingContent(Flux<String> content) {
        this.streamingContent = Objects.requireNonNull(content, "content");
    }

    /** Returns the system message most recently received in a prompt. */
    public SystemMessage lastSystemMessage() {
        return lastSystemMessage.get();
    }

    /** Returns the user message most recently received in a prompt. */
    public UserMessage lastUserMessage() {
        return lastUserMessage.get();
    }

    /** Returns the number of synchronous model invocations. */
    public int calls() {
        return calls.get();
    }

    /** Returns the number of streaming model invocations. */
    public int streams() {
        return streams.get();
    }

    private void capture(Prompt prompt) {
        lastSystemMessage.set(prompt.getSystemMessage());
        lastUserMessage.set(prompt.getUserMessage());
    }

    private static ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}
