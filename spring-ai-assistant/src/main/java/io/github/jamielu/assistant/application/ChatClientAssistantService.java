package io.github.jamielu.assistant.application;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Objects;

/** Application service that owns the small ChatClient prompt workflow. */
@Service
public final class ChatClientAssistantService implements AssistantService {
    private final ChatClient chatClient;

    /**
     * Uses the application-configured ChatClient.
     *
     * @param chatClient client built at the application configuration boundary
     */
    public ChatClientAssistantService(ChatClient chatClient) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient");
    }

    @Override
    public String chat(String userContent) {
        validate(userContent);
        try {
            String content = chatClient.prompt()
                    .user(userContent)
                    .call()
                    .content();
            if (content == null) {
                throw new AssistantModelException("assistant model returned null content");
            }
            return content;
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
