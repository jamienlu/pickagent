package com.pickagent.w2.reliability;

import java.time.Duration;

/** A closed retry decision: either retry after a delay or stop with a reason. */
public sealed interface RetryDecision permits RetryDecision.RetryAfter, RetryDecision.Stop {
    /**
     * Retry after at least {@code delay}. This type does not perform the wait.
     *
     * @param delay non-negative delay before the next attempt
     */
    record RetryAfter(Duration delay) implements RetryDecision {
        /** Validates the retry delay. */
        public RetryAfter {
            if (delay == null || delay.isNegative()) {
                throw new IllegalArgumentException("delay must be non-negative");
            }
        }
    }

    /**
     * Stop retrying for the supplied diagnostic reason.
     *
     * @param reason non-blank diagnostic reason
     */
    record Stop(String reason) implements RetryDecision {
        /** Validates the diagnostic reason. */
        public Stop {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reason cannot be null or blank");
            }
        }
    }
}
