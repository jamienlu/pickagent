package com.pickagent.w2.reliability;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryPolicyTest {
    @Test
    void transientRateLimitUsesDeterministicBackoffAndInjectedJitter() {
        RetryPolicy policy = policy(5, Duration.ofSeconds(30),
                backoff -> Duration.ofMillis(250));

        var retry = assertInstanceOf(RetryDecision.RetryAfter.class,
                policy.decide(FailureKind.TRANSIENT_RATE_LIMIT, 1,
                        Duration.ZERO, Optional.empty()));

        assertEquals(Duration.ofMillis(1250), retry.delay());
    }

    @Test
    void serviceOverloadIsRetryable() {
        var retry = assertInstanceOf(RetryDecision.RetryAfter.class,
                policy(3, Duration.ofSeconds(30), ignored -> Duration.ZERO)
                        .decide(FailureKind.SERVICE_OVERLOADED, 1,
                                Duration.ZERO, Optional.empty()));

        assertEquals(Duration.ofSeconds(1), retry.delay());
    }

    @Test
    void timeoutIsRetryableWithinBudgets() {
        var retry = assertInstanceOf(RetryDecision.RetryAfter.class,
                policy(3, Duration.ofSeconds(30), ignored -> Duration.ZERO)
                        .decide(FailureKind.TIMEOUT, 1,
                                Duration.ZERO, Optional.empty()));

        assertEquals(Duration.ofSeconds(1), retry.delay());
    }

    @Test
    void retryAfterIsTreatedAsTheMinimumDelayAndJitterIsAdded() {
        var retry = assertInstanceOf(RetryDecision.RetryAfter.class,
                policy(4, Duration.ofSeconds(30), ignored -> Duration.ofMillis(250))
                        .decide(FailureKind.TRANSIENT_RATE_LIMIT, 1,
                                Duration.ZERO, Optional.of(Duration.ofSeconds(5))));

        assertEquals(Duration.ofMillis(5250), retry.delay());
    }

    @Test
    void exponentialBackoffPlusJitterIsCapped() {
        RetryPolicy policy = new RetryPolicy(8, Duration.ofSeconds(60),
                Duration.ofSeconds(1), Duration.ofSeconds(8),
                ignored -> Duration.ofSeconds(3));

        var thirdRetry = assertInstanceOf(RetryDecision.RetryAfter.class,
                policy.decide(FailureKind.TRANSIENT_RATE_LIMIT, 3,
                        Duration.ZERO, Optional.empty()));
        var fourthRetry = assertInstanceOf(RetryDecision.RetryAfter.class,
                policy.decide(FailureKind.TRANSIENT_RATE_LIMIT, 4,
                        Duration.ZERO, Optional.empty()));

        assertEquals(Duration.ofSeconds(7), thirdRetry.delay());
        assertEquals(Duration.ofSeconds(8), fourthRetry.delay());
    }

    @Test
    void attemptBudgetStopsImmediately() {
        var stop = assertInstanceOf(RetryDecision.Stop.class,
                policy(3, Duration.ofSeconds(30), ignored -> Duration.ZERO)
                        .decide(FailureKind.TRANSIENT_RATE_LIMIT, 3,
                                Duration.ZERO, Optional.empty()));

        assertEquals("attempt budget exhausted: 3", stop.reason());
    }

    @Test
    void exhaustedTotalWaitBudgetStopsImmediatelyWithoutConsultingJitter() {
        RetryPolicy policy = policy(5, Duration.ofSeconds(10), ignored -> {
            throw new AssertionError("jitter must not be consulted after the budget is exhausted");
        });

        var stop = assertInstanceOf(RetryDecision.Stop.class,
                policy.decide(FailureKind.TRANSIENT_RATE_LIMIT, 1,
                        Duration.ofSeconds(10), Optional.empty()));

        assertEquals("total wait budget exhausted: PT10S", stop.reason());
    }

    @Test
    void nextDelayThatWouldExceedTotalBudgetStops() {
        var stop = assertInstanceOf(RetryDecision.Stop.class,
                policy(5, Duration.ofSeconds(10), ignored -> Duration.ZERO)
                        .decide(FailureKind.TRANSIENT_RATE_LIMIT, 2,
                                Duration.ofSeconds(9), Optional.empty()));

        assertEquals("next delay would exceed total wait budget: PT10S", stop.reason());
    }

    @Test
    void retryAfterPlusJitterStillRespectsTheTotalWaitBudget() {
        var stop = assertInstanceOf(RetryDecision.Stop.class,
                policy(5, Duration.ofSeconds(6), ignored -> Duration.ofMillis(250))
                        .decide(FailureKind.TRANSIENT_RATE_LIMIT, 1,
                                Duration.ofSeconds(1), Optional.of(Duration.ofSeconds(5))));

        assertEquals("next delay would exceed total wait budget: PT6S", stop.reason());
    }

    @Test
    void billingAuthenticationAndInvalidRequestsNeverRetry() {
        RetryPolicy policy = policy(5, Duration.ofSeconds(30), ignored -> {
            throw new AssertionError("jitter must not be consulted for permanent failures");
        });

        for (FailureKind kind : List.of(FailureKind.BILLING_OR_QUOTA,
                FailureKind.AUTHENTICATION, FailureKind.INVALID_REQUEST)) {
            var stop = assertInstanceOf(RetryDecision.Stop.class,
                    policy.decide(kind, 1, Duration.ZERO, Optional.empty()));
            assertEquals("failure is not retryable: " + kind, stop.reason());
        }
    }

    @Test
    void invalidAttemptAndRetryAfterAreRejectedAsCallerBugs() {
        RetryPolicy policy = policy(3, Duration.ofSeconds(30), ignored -> Duration.ZERO);

        assertEquals("attemptsMade must be positive",
                assertThrows(IllegalArgumentException.class,
                        () -> policy.decide(FailureKind.TIMEOUT, 0,
                                Duration.ZERO, Optional.empty())).getMessage());
        assertEquals("retryAfter must be non-negative",
                assertThrows(IllegalArgumentException.class,
                        () -> policy.decide(FailureKind.TIMEOUT, 1, Duration.ZERO,
                                Optional.of(Duration.ofSeconds(-1)))).getMessage());
    }

    private static RetryPolicy policy(int maxAttempts, Duration maxTotalWait,
                                      RetryPolicy.JitterSource jitterSource) {
        return new RetryPolicy(maxAttempts, maxTotalWait, Duration.ofSeconds(1),
                Duration.ofSeconds(8), jitterSource);
    }
}
