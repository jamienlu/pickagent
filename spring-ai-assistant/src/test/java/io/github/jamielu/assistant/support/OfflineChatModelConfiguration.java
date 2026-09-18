package io.github.jamielu.assistant.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** 为完整 Spring 上下文测试提供确定性的 ChatModel。 */
@TestConfiguration(proxyBeanMethods = false)
public class OfflineChatModelConfiguration {
    /** 返回不访问网络的模型替身。 */
    @Bean
    public DeterministicChatModel deterministicChatModel() {
        return new DeterministicChatModel();
    }

}
