package io.github.jamielu.assistant.integration.springai;

import io.github.jamielu.assistant.application.AssistantAnswer;
import io.github.jamielu.assistant.application.AssistantModelException;
import io.github.jamielu.assistant.application.TokenUsage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

/** Maps Spring AI response types into provider-neutral application values. */
@Component
public final class SpringAiChatResponseMapper {

    /**
     * Maps one complete response without exposing Spring AI metadata downstream.
     *
     * @param response complete Spring AI response
     * @return application-owned answer
     */
    public AssistantAnswer map(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new AssistantModelException("assistant model returned no result");
        }

        String message = response.getResult().getOutput().getText();
        if (message == null) {
            throw new AssistantModelException("assistant model returned null content");
        }

        var metadata = response.getMetadata();
        if (metadata == null) {
            return new AssistantAnswer(message, null, null, null);
        }

        return new AssistantAnswer(
                message,
                unknownWhenBlank(metadata.getId()),
                unknownWhenBlank(metadata.getModel()),
                mapUsage(metadata.getUsage()));
    }

    private static TokenUsage mapUsage(Usage usage) {
        if (usage == null || usage instanceof EmptyUsage) {
            return null;
        }
        return new TokenUsage(
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                usage.getTotalTokens());
    }

    private static String unknownWhenBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
