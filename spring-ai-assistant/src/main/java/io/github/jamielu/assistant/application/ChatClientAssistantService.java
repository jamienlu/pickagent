package io.github.jamielu.assistant.application;

import io.github.jamielu.assistant.integration.springai.SpringAiChatResponseMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Objects;

/** Application service that owns the small ChatClient prompt workflow. */
@Service
public final class ChatClientAssistantService implements AssistantService {
    private final ChatClient chatClient;
    private final SpringAiChatResponseMapper responseMapper;

    /**
     * Uses the application-configured ChatClient.
     *
     * @param chatClient client built at the application configuration boundary
     * @param responseMapper Spring AI to application response boundary
     */
    public ChatClientAssistantService(
            ChatClient chatClient,
            SpringAiChatResponseMapper responseMapper) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient");
        this.responseMapper = Objects.requireNonNull(responseMapper, "responseMapper");
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
        try {
            return chatClient.prompt()
                    .user(userContent)
                    .stream()
                    .content()
                    .onErrorMap(failure -> failure instanceof AssistantModelException
                            ? failure
                            : new AssistantModelException(failure));
        } catch (RuntimeException failure) {
            return Flux.error(new AssistantModelException(failure));
        }
    }

    private static void validate(String userContent) {
        if (userContent == null || userContent.isBlank()) {
            throw new InvalidAssistantInputException();
        }
    }
}
