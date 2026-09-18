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

/** 所有测试共用的确定性、无网络 ChatModel。 */
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

    /** 设置 {@link #call(Prompt)} 返回的完整响应。 */
    public void synchronousContent(String content) {
        this.synchronousResponse = response(Objects.requireNonNull(content, "content"));
        this.synchronousFailure = null;
    }

    /** 设置包含确定性元数据与用量的完整响应。 */
    public void synchronousResponse(ChatResponse response) {
        this.synchronousResponse = Objects.requireNonNull(response, "response");
        this.synchronousFailure = null;
    }

    /** 让同步调用以确定方式失败。 */
    public void synchronousFailure(RuntimeException failure) {
        this.synchronousFailure = Objects.requireNonNull(failure, "failure");
    }

    /** 设置有序且可以失败或取消的流式测试数据。 */
    public void streamingContent(Flux<String> content) {
        this.streamingContent = Objects.requireNonNull(content, "content");
    }

    /** 返回最近一次提示词中收到的系统消息。 */
    public SystemMessage lastSystemMessage() {
        return lastSystemMessage.get();
    }

    /** 返回最近一次提示词中收到的用户消息。 */
    public UserMessage lastUserMessage() {
        return lastUserMessage.get();
    }

    /** 返回同步模型调用次数。 */
    public int calls() {
        return calls.get();
    }

    /** 返回流式模型调用次数。 */
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
