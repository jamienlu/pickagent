package io.github.jamielu.assistant.application;

import io.github.jamielu.assistant.config.AssistantStreamProperties;
import io.github.jamielu.assistant.integration.springai.SpringAiChatResponseMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Objects;

/** 负责精简 ChatClient 提示词工作流的应用服务。 */
@Service
public final class ChatClientAssistantService implements AssistantService {
    private final ChatClient chatClient;
    private final SpringAiChatResponseMapper responseMapper;
    private final AssistantStreamProperties streamProperties;

    /**
     * 使用由应用配置完成的 ChatClient。
     *
     * @param chatClient 在应用配置边界构建的客户端
     * @param responseMapper Spring AI 响应到应用响应的映射边界
     * @param streamProperties 已校验的流式时间策略
     */
    public ChatClientAssistantService(
            ChatClient chatClient,
            SpringAiChatResponseMapper responseMapper,
            AssistantStreamProperties streamProperties) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient");
        this.responseMapper = Objects.requireNonNull(responseMapper, "responseMapper");
        this.streamProperties = Objects.requireNonNull(streamProperties, "streamProperties");
    }

    @Override
    public AssistantAnswer chat(String userContent) {
        validate(userContent);
        try {
            var response = chatClient.prompt()
                    .user(userContent)
                    .call()
                    .chatResponse();
            return responseMapper.map(response);
        } catch (AssistantModelException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new AssistantModelException(failure);
        }
    }

    @Override
    public Flux<String> stream(String userContent) {
        validate(userContent);
        return Flux.defer(() -> chatClient.prompt()
                    .user(userContent)
                    .stream()
                    .content())
                .timeout(streamProperties.signalTimeout())
                .onErrorMap(failure -> failure instanceof AssistantModelException
                        ? failure
                        : new AssistantModelException(failure));
    }

    private static void validate(String userContent) {
        if (userContent == null || userContent.isBlank()) {
            throw new InvalidAssistantInputException();
        }
    }
}
