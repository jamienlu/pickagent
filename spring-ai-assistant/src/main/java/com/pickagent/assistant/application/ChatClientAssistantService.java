package com.pickagent.assistant.application;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Objects;

/** Application service that owns the small ChatClient prompt workflow. */
@Service
public final class ChatClientAssistantService implements AssistantService {
    private final ChatClient chatClient;

    /**
     * Builds a client from Spring AI's auto-configured prototype builder.
     *
     * @param builder auto-configured ChatClient builder
     */
    public ChatClientAssistantService(ChatClient.Builder builder) {
        this.chatClient = Objects.requireNonNull(builder, "builder").build();
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
