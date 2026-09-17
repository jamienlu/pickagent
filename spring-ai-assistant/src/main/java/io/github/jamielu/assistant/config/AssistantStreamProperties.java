package io.github.jamielu.assistant.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Application-owned timing policy for assistant text streams. */
@Validated
@ConfigurationProperties(prefix = "assistant.stream")
public record AssistantStreamProperties(
        @NotNull(message = "assistant.stream.signal-timeout must be configured") Duration signalTimeout) {

    /** Requires a strictly positive per-signal timeout. */
    @AssertTrue(message = "assistant.stream.signal-timeout must be greater than zero")
    public boolean isSignalTimeoutPositive() {
        return signalTimeout != null && !signalTimeout.isZero() && !signalTimeout.isNegative();
    }
}
