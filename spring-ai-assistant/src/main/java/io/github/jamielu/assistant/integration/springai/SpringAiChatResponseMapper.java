package io.github.jamielu.assistant.integration.springai;

import io.github.jamielu.assistant.application.AssistantAnswer;
import io.github.jamielu.assistant.application.AssistantModelException;
import io.github.jamielu.assistant.application.TokenUsage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

/** 将 Spring AI 响应类型映射为与供应商无关的应用值对象。 */
@Component
public final class SpringAiChatResponseMapper {
    /** 创建 Spring AI 响应映射器。 */
    public SpringAiChatResponseMapper() {
    }

    /**
     * 映射一条完整响应，不向下游暴露 Spring AI 元数据类型。
     *
     * @param response 完整的 Spring AI 响应
     * @return 由应用持有的回答
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
