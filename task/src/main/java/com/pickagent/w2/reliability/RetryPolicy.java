package com.pickagent.w2.reliability;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure retry decision logic with bounded exponential backoff and injected jitter.
 *
 * <p>{@code attemptsMade} includes the request that has just failed. For
 * example, {@code attemptsMade == 1} asks whether to schedule the first retry.
 * The total-time budget in this exercise is represented by cumulative waiting
 * time; request execution time can be added to that value by a production
 * orchestrator if its budget includes both execution and waiting.</p>
 */
public final class RetryPolicy {
    /** Supplies a non-negative jitter duration for a calculated backoff. */
    @FunctionalInterface
    public interface JitterSource {
        /**
         * Calculates jitter for one exponential delay.
         *
         * @param exponentialBackoff bounded exponential component
         * @return a non-negative jitter duration
         */
        Duration jitterFor(Duration exponentialBackoff);
    }

    private final int maxAttempts;
    private final Duration maxTotalWait;
    private final Duration baseDelay;
    private final Duration maxBackoff;
    private final JitterSource jitterSource;

    /**
     * Creates a retry policy.
     *
     * @param maxAttempts maximum total attempts, including the initial request
     * @param maxTotalWait maximum cumulative retry delay
     * @param baseDelay delay used before the first retry
     * @param maxBackoff upper bound for calculated backoff plus jitter
     * @param jitterSource deterministic or random jitter provider
     */
    public RetryPolicy(int maxAttempts, Duration maxTotalWait, Duration baseDelay,
                       Duration maxBackoff, JitterSource jitterSource) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        requireNonNegative(maxTotalWait, "maxTotalWait");
        requirePositive(baseDelay, "baseDelay");
        requirePositive(maxBackoff, "maxBackoff");
        if (maxBackoff.compareTo(baseDelay) < 0) {
            throw new IllegalArgumentException("maxBackoff must be at least baseDelay");
        }
        this.maxAttempts = maxAttempts;
        this.maxTotalWait = maxTotalWait;
        this.baseDelay = baseDelay;
        this.maxBackoff = maxBackoff;
        this.jitterSource = Objects.requireNonNull(jitterSource, "jitterSource");
    }

    /**
     * Decides whether and when another attempt may be made.
     *
     * @param failureKind provider-neutral failure category
     * @param attemptsMade attempts already made, including the just-failed one
     * @param totalWaitSoFar retry delay already consumed
     * @param retryAfter optional server-provided minimum delay
     * @return a retry delay or a terminal stop reason
     */
    public RetryDecision decide(FailureKind failureKind, int attemptsMade,
                                Duration totalWaitSoFar, Optional<Duration> retryAfter) {
        Objects.requireNonNull(failureKind, "failureKind");
        if (attemptsMade < 1) {
            throw new IllegalArgumentException("attemptsMade must be positive");
        }
        requireNonNegative(totalWaitSoFar, "totalWaitSoFar");
        Objects.requireNonNull(retryAfter, "retryAfter");
        retryAfter.ifPresent(value -> requireNonNegative(value, "retryAfter"));

        if (!failureKind.retryable()) {
            return new RetryDecision.Stop("failure is not retryable: " + failureKind);
        }
        if (attemptsMade >= maxAttempts) {
            return new RetryDecision.Stop("attempt budget exhausted: " + maxAttempts);
        }
        if (totalWaitSoFar.compareTo(maxTotalWait) >= 0) {
            return new RetryDecision.Stop("total wait budget exhausted: " + maxTotalWait);
        }

        Duration exponential = exponentialDelay(attemptsMade);
        Duration jitter = Objects.requireNonNull(jitterSource.jitterFor(exponential),
                "jitterSource result");
        requireNonNegative(jitter, "jitter");
        Duration calculated = min(saturatedAdd(exponential, jitter), maxBackoff);
        Duration delay = retryAfter.map(value -> max(value, calculated)).orElse(calculated);

        if (saturatedAdd(totalWaitSoFar, delay).compareTo(maxTotalWait) > 0) {
            return new RetryDecision.Stop("next delay would exceed total wait budget: " + maxTotalWait);
        }
        return new RetryDecision.RetryAfter(delay);
    }

    private Duration exponentialDelay(int attemptsMade) {
        Duration result = baseDelay;
        for (int retryNumber = 1; retryNumber < attemptsMade && result.compareTo(maxBackoff) < 0;
             retryNumber++) {
            result = min(saturatedAdd(result, result), maxBackoff);
        }
        return result;
    }

    private static Duration saturatedAdd(Duration left, Duration right) {
        try {
            return left.plus(right);
        } catch (ArithmeticException overflow) {
            return Duration.ofSeconds(Long.MAX_VALUE);
        }
    }

    private static Duration min(Duration left, Duration right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private static Duration max(Duration left, Duration right) {
        return left.compareTo(right) >= 0 ? left : right;
    }

    private static void requirePositive(Duration value, String field) {
        requireNonNegative(value, field);
        if (value.isZero()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private static void requireNonNegative(Duration value, String field) {
        if (value == null || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }
}
