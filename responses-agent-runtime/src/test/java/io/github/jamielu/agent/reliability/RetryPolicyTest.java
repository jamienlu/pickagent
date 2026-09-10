package io.github.jamielu.agent.reliability;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryPolicyTest {
    // 场景：临时限流使用确定性指数退避和注入抖动；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void transientRateLimitUsesDeterministicBackoffAndInjectedJitter() {
        RetryPolicy policy = policy(5, Duration.ofSeconds(30),
                backoff -> Duration.ofMillis(250));

        var retry = assertInstanceOf(RetryDecision.RetryAfter.class,
                policy.decide(FailureKind.TRANSIENT_RATE_LIMIT, 1,
                        Duration.ZERO, Optional.empty()));

        assertEquals(Duration.ofMillis(1250), retry.delay());
    }

    // 场景：服务过载在预算内允许重试；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void serviceOverloadIsRetryable() {
        var retry = assertInstanceOf(RetryDecision.RetryAfter.class,
                policy(3, Duration.ofSeconds(30), ignored -> Duration.ZERO)
                        .decide(FailureKind.SERVICE_OVERLOADED, 1,
                                Duration.ZERO, Optional.empty()));

        assertEquals(Duration.ofSeconds(1), retry.delay());
    }

    // 场景：超时在尝试和等待预算内允许重试；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void timeoutIsRetryableWithinBudgets() {
        var retry = assertInstanceOf(RetryDecision.RetryAfter.class,
                policy(3, Duration.ofSeconds(30), ignored -> Duration.ZERO)
                        .decide(FailureKind.TIMEOUT, 1,
                                Duration.ZERO, Optional.empty()));

        assertEquals(Duration.ofSeconds(1), retry.delay());
    }

    // 场景：服务器等待值作为下限并额外加入抖动；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void retryAfterIsTreatedAsTheMinimumDelayAndJitterIsAdded() {
        var retry = assertInstanceOf(RetryDecision.RetryAfter.class,
                policy(4, Duration.ofSeconds(30), ignored -> Duration.ofMillis(250))
                        .decide(FailureKind.TRANSIENT_RATE_LIMIT, 1,
                                Duration.ZERO, Optional.of(Duration.ofSeconds(5))));

        assertEquals(Duration.ofMillis(5250), retry.delay());
    }

    // 场景：指数退避与抖动之和受最大回退上限约束；行为：执行对应代码路径；预期：相关业务断言全部成立。
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
        var fifthRetry = assertInstanceOf(RetryDecision.RetryAfter.class,
                policy.decide(FailureKind.TRANSIENT_RATE_LIMIT, 5,
                        Duration.ZERO, Optional.empty()));

        assertEquals(Duration.ofSeconds(7), thirdRetry.delay());
        assertEquals(Duration.ofSeconds(8), fourthRetry.delay());
        assertEquals(Duration.ofSeconds(8), fifthRetry.delay());
    }

    // 场景：尝试次数预算耗尽时立即停止；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void attemptBudgetStopsImmediately() {
        var stop = assertInstanceOf(RetryDecision.Stop.class,
                policy(3, Duration.ofSeconds(30), ignored -> Duration.ZERO)
                        .decide(FailureKind.TRANSIENT_RATE_LIMIT, 3,
                                Duration.ZERO, Optional.empty()));

        assertEquals("attempt budget exhausted: 3", stop.reason());
    }

    // 场景：总等待预算耗尽时不再咨询抖动源；行为：执行对应代码路径；预期：相关业务断言全部成立。
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

    // 场景：下一次等待将超出总预算时停止；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void nextDelayThatWouldExceedTotalBudgetStops() {
        var stop = assertInstanceOf(RetryDecision.Stop.class,
                policy(5, Duration.ofSeconds(10), ignored -> Duration.ZERO)
                        .decide(FailureKind.TRANSIENT_RATE_LIMIT, 2,
                                Duration.ofSeconds(9), Optional.empty()));

        assertEquals("next delay would exceed total wait budget: PT10S", stop.reason());
    }

    // 场景：服务器等待与抖动之和仍受总等待预算限制；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void retryAfterPlusJitterStillRespectsTheTotalWaitBudget() {
        var stop = assertInstanceOf(RetryDecision.Stop.class,
                policy(5, Duration.ofSeconds(6), ignored -> Duration.ofMillis(250))
                        .decide(FailureKind.TRANSIENT_RATE_LIMIT, 1,
                                Duration.ofSeconds(1), Optional.of(Duration.ofSeconds(5))));

        assertEquals("next delay would exceed total wait budget: PT6S", stop.reason());
    }

    // 场景：账单、鉴权和无效请求失败均不自动重试；行为：执行对应代码路径；预期：相关业务断言全部成立。
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

    // 场景：非法尝试次数和服务器等待值作为调用方错误拒绝；行为：执行对应代码路径；预期：相关业务断言全部成立。
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

    // 场景：构造重试策略时各预算字段非法；行为：执行构造校验；预期：逐项拒绝非法范围和空依赖。
    @Test
    void constructorRejectsInvalidPolicyConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new RetryPolicy(0, Duration.ZERO, Duration.ofSeconds(1),
                        Duration.ofSeconds(1), ignored -> Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new RetryPolicy(1, Duration.ofNanos(-1), Duration.ofSeconds(1),
                        Duration.ofSeconds(1), ignored -> Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new RetryPolicy(1, Duration.ZERO, Duration.ZERO,
                        Duration.ofSeconds(1), ignored -> Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new RetryPolicy(1, Duration.ZERO, Duration.ofSeconds(2),
                        Duration.ofSeconds(1), ignored -> Duration.ZERO));
        assertThrows(NullPointerException.class,
                () -> new RetryPolicy(1, Duration.ZERO, Duration.ofSeconds(1),
                        Duration.ofSeconds(1), null));
        assertThrows(IllegalArgumentException.class,
                () -> new RetryPolicy(1, null, Duration.ofSeconds(1),
                        Duration.ofSeconds(1), ignored -> Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new RetryPolicy(1, Duration.ZERO, null,
                        Duration.ofSeconds(1), ignored -> Duration.ZERO));
    }

    // 场景：决策入口收到空或非法调用状态；行为：校验调用参数；预期：在咨询抖动源前失败。
    @Test
    void decisionRejectsInvalidInputsAndJitter() {
        RetryPolicy policy = policy(3, Duration.ofSeconds(30), ignored -> Duration.ZERO);

        assertThrows(NullPointerException.class,
                () -> policy.decide(null, 1, Duration.ZERO, Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> policy.decide(FailureKind.TIMEOUT, 1,
                        Duration.ofNanos(-1), Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> policy.decide(FailureKind.TIMEOUT, 1,
                        null, Optional.empty()));
        assertThrows(NullPointerException.class,
                () -> policy.decide(FailureKind.TIMEOUT, 1,
                        Duration.ZERO, null));

        RetryPolicy nullJitter = policy(3, Duration.ofSeconds(30), ignored -> null);
        assertThrows(NullPointerException.class,
                () -> nullJitter.decide(FailureKind.TIMEOUT, 1,
                        Duration.ZERO, Optional.empty()));
        RetryPolicy negativeJitter = policy(3, Duration.ofSeconds(30),
                ignored -> Duration.ofNanos(-1));
        assertThrows(IllegalArgumentException.class,
                () -> negativeJitter.decide(FailureKind.TIMEOUT, 1,
                        Duration.ZERO, Optional.empty()));
    }

    // 场景：服务器最小等待小于本地退避；行为：合并两种等待来源；预期：选用较大的本地计算值。
    @Test
    void calculatedBackoffWinsOverSmallerRetryAfter() {
        RetryPolicy policy = policy(4, Duration.ofSeconds(30),
                ignored -> Duration.ofMillis(250));

        RetryDecision.RetryAfter retry = assertInstanceOf(
                RetryDecision.RetryAfter.class,
                policy.decide(FailureKind.TIMEOUT, 2, Duration.ZERO,
                        Optional.of(Duration.ofMillis(100))));

        assertEquals(Duration.ofMillis(2250), retry.delay());
    }

    // 场景：退避加法超出 Duration 表示范围；行为：计算下一等待；预期：饱和到最大值而不抛出算术异常。
    @Test
    void overflowingBackoffSaturates() {
        Duration maximum = Duration.ofSeconds(Long.MAX_VALUE);
        RetryPolicy policy = new RetryPolicy(2, maximum, maximum, maximum,
                ignored -> maximum);

        RetryDecision.RetryAfter retry = assertInstanceOf(
                RetryDecision.RetryAfter.class,
                policy.decide(FailureKind.TIMEOUT, 1, Duration.ZERO, Optional.empty()));

        assertEquals(maximum, retry.delay());
    }

    private static RetryPolicy policy(int maxAttempts, Duration maxTotalWait,
                                      RetryPolicy.JitterSource jitterSource) {
        return new RetryPolicy(maxAttempts, maxTotalWait, Duration.ofSeconds(1),
                Duration.ofSeconds(8), jitterSource);
    }
}


