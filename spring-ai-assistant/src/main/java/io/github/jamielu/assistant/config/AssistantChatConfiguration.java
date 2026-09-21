package io.github.jamielu.assistant.config;

import io.github.jamielu.assistant.tools.AssistantPolicyTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 将应用提示词、生成策略与显式工具白名单组合进 Spring AI 客户端。 */
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
     * 从已校验配置创建唯一允许暴露的只读工具实例。
     *
     * @param streamProperties 已校验的流式时间策略
     * @param generationProperties 已校验的文本生成策略
     * @return 不访问网络、文件或数据库的策略查询工具
     */
    @Bean
    public AssistantPolicyTools assistantPolicyTools(
            AssistantStreamProperties streamProperties,
            AssistantGenerationProperties generationProperties) {
        return new AssistantPolicyTools(
                streamProperties.signalTimeout(),
                generationProperties.maxOutputTokens());
    }

    /**
     * 使用默认系统消息构建应用唯一的 ChatClient。
     *
     * @param builder Spring AI 自动配置、原型作用域的构建器
     * @param promptProperties 已校验的应用提示词策略
     * @param generationProperties 已校验的通用文本生成策略
     * @param policyTools 显式允许暴露给模型的只读策略工具
     * @return 配置完成的应用 ChatClient
     */
    @Bean
    public ChatClient assistantChatClient(
            ChatClient.Builder builder,
            AssistantPromptProperties promptProperties,
            AssistantGenerationProperties generationProperties,
            AssistantPolicyTools policyTools) {
        return builder
                .defaultSystem(promptProperties.system())
                .defaultOptions(ChatOptions.builder()
                        .maxTokens(generationProperties.maxOutputTokens()))
                .defaultTools(policyTools)
                .build();
    }
}
