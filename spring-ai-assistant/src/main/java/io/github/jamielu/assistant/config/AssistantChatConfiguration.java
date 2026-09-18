package io.github.jamielu.assistant.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 将应用提示词策略与 Spring AI 自动配置的客户端构建器组合起来。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        AssistantPromptProperties.class,
        AssistantStreamProperties.class,
        AssistantGenerationProperties.class
})
public class AssistantChatConfiguration {
    /** 创建应用的 ChatClient 配置。 */
    public AssistantChatConfiguration() {
    }

    /**
     * 使用默认系统消息构建应用唯一的 ChatClient。
     *
     * @param builder Spring AI 自动配置、原型作用域的构建器
     * @param promptProperties 已校验的应用提示词策略
     * @param generationProperties 已校验的通用文本生成策略
     * @return 配置完成的应用 ChatClient
     */
    @Bean
    public ChatClient assistantChatClient(
            ChatClient.Builder builder,
            AssistantPromptProperties promptProperties,
            AssistantGenerationProperties generationProperties) {
        return builder
                .defaultSystem(promptProperties.system())
                .defaultOptions(ChatOptions.builder()
                        .maxTokens(generationProperties.maxOutputTokens()))
                .build();
    }
}
