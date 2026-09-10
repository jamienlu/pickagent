package io.github.jamielu.agent.reliability;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** 使用有界指数退避和可注入抖动的纯重试决策逻辑。 */
public final class RetryPolicy {
    /** 提供计算后退避所需的非负抖动时长。 */
    @FunctionalInterface
    public interface JitterSource {
        /**
         * 为一次指数退避计算抖动。
         *
         * @param exponentialBackoff 当前指数退避时长
         * @return 需要追加的非负抖动时长
         */
        Duration jitterFor(Duration exponentialBackoff);
    }

    private final int maxAttempts;
    private final Duration maxTotalWait;
    private final Duration baseDelay;
    private final Duration maxBackoff;
    private final JitterSource jitterSource;

    /**
     * 创建重试策略。
     *
     * @param maxAttempts 包含首次尝试的最大尝试次数
     * @param maxTotalWait 允许累计等待的最大时长
     * @param baseDelay 首次重试的基础等待时长
     * @param maxBackoff 单次计算退避上限
     * @param jitterSource 可注入抖动来源
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
     * 判断是否重试以及下一次重试等待时长。
     *
     * @param failureKind 供应商中立失败分类
     * @param attemptsMade 已完成尝试次数
     * @param totalWaitSoFar 已累计等待时长
     * @param retryAfter 可选服务端最小等待建议
     * @return 等待后重试或停止决策
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
        Duration delay = retryAfter
                .map(value -> max(saturatedAdd(value, jitter), calculated))
                .orElse(calculated);

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


