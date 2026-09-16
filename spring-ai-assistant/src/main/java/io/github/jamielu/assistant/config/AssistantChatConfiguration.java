package io.github.jamielu.assistant.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composes application prompt policy with Spring AI's auto-configured client builder. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AssistantPromptProperties.class)
public class AssistantChatConfiguration {

    /**
     * Builds the application's single ChatClient with its default system message.
     *
     * @param builder Spring AI's prototype-scoped, auto-configured builder
     * @param promptProperties validated application prompt policy
     * @return the configured application ChatClient
     */
    @Bean
    public ChatClient assistantChatClient(
            ChatClient.Builder builder,
            AssistantPromptProperties promptProperties) {
        return builder.defaultSystem(promptProperties.system()).build();
    }
}
