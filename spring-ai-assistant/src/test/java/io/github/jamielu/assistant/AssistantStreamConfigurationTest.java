package io.github.jamielu.assistant;

import io.github.jamielu.assistant.config.AssistantStreamProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证流式逐信号超时配置的绑定与启动期校验。 */
class AssistantStreamConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(StreamPropertiesConfiguration.class);

    /** 验证外部属性能够把默认逐信号超时覆盖为毫秒级 Duration。 */
    @Test
    void externalPropertyOverridesTheDefaultSignalTimeout() {
        contextRunner
                .withPropertyValues("assistant.stream.signal-timeout=750ms")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AssistantStreamProperties.class).signalTimeout())
                            .isEqualTo(Duration.ofMillis(750));
                });
    }

    /** 参数矩阵覆盖空值、零和负数，预期三种非法超时均阻止应用启动。 */
    @ParameterizedTest(name = "非法逐信号超时 [{0}] 应导致启动失败")
    @ValueSource(strings = {"", "0s", "-1s"})
    void blankZeroAndNegativeTimeoutsFailStartup(String configuredValue) {
        contextRunner
                .withPropertyValues("assistant.stream.signal-timeout=" + configuredValue)
                .run(context -> {
                    assertThat(context).hasFailed();
                    String failureMessages = causeMessages(context.getStartupFailure());
                    System.out.printf(
                            "invalid.stream.signal-timeout=[%s] startup.failure=%s%n",
                            configuredValue,
                            failureMessages);
                    assertThat(failureMessages).contains("assistant.stream");
                });
    }

    private static String causeMessages(Throwable failure) {
        return Stream.iterate(failure, cause -> cause != null, Throwable::getCause)
                .map(Throwable::getMessage)
                .filter(message -> message != null && !message.isBlank())
                .collect(Collectors.joining(" | "));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AssistantStreamProperties.class)
    static class StreamPropertiesConfiguration {
    }
}
