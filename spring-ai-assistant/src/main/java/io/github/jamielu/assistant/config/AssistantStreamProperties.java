package io.github.jamielu.assistant.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 由应用持有的助手文本流时间策略。
 *
 * @param signalTimeout 等待首个或相邻文本信号的最长时间
 */
@Validated
@ConfigurationProperties(prefix = "assistant.stream")
public record AssistantStreamProperties(
        @NotNull(message = "assistant.stream.signal-timeout must be configured") Duration signalTimeout) {

    /**
     * 要求逐信号超时时间严格大于零。
     *
     * @return 超时时间有效时返回 {@code true}
     */
    @AssertTrue(message = "assistant.stream.signal-timeout must be greater than zero")
    public boolean isSignalTimeoutPositive() {
        return signalTimeout != null && !signalTimeout.isZero() && !signalTimeout.isNegative();
    }
}
