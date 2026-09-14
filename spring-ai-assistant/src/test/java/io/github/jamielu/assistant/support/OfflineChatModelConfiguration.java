package io.github.jamielu.assistant.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Supplies the deterministic ChatModel for full Spring context tests. */
@TestConfiguration(proxyBeanMethods = false)
public class OfflineChatModelConfiguration {
    /** Returns a network-free model substitute. */
    @Bean
    public DeterministicChatModel deterministicChatModel() {
        return new DeterministicChatModel();
    }

}
