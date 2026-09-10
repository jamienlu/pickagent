package io.github.jamielu.agent.reliability;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdempotentExecutorTest {
    // 场景：相同业务键与请求重放首个结果且副作用仅执行一次；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void sameKeyAndSameRequestReturnsFirstResultAndExecutesSideEffectOnce() throws Exception {
        IdempotentExecutor<String> executor = executor();
        AtomicInteger sideEffects = new AtomicInteger();

        String first = executor.execute("op-001", "sha256:request-a",
                () -> "receipt-" + sideEffects.incrementAndGet());
        String replay = executor.execute("op-001", "sha256:request-a",
                () -> "must-not-run-" + sideEffects.incrementAndGet());

        assertEquals("receipt-1", first);
        assertEquals(first, replay);
        assertEquals(1, sideEffects.get());
    }

    // 场景：同一业务键对应不同请求时在第二次副作用前拒绝；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void sameKeyAndDifferentRequestIsRejectedBeforeSecondSideEffect() throws Exception {
        IdempotentExecutor<String> executor = executor();
        AtomicInteger sideEffects = new AtomicInteger();
        executor.execute("op-001", "sha256:request-a",
                () -> "receipt-" + sideEffects.incrementAndGet());

        var conflict = assertThrows(IdempotentExecutor.IdempotencyConflictException.class,
                () -> executor.execute("op-001", "sha256:request-b",
                        () -> "must-not-run-" + sideEffects.incrementAndGet()));

        assertEquals("operationKey already belongs to a different request: op-001",
                conflict.getMessage());
        assertEquals(1, sideEffects.get());
    }

    // 场景：不同业务键各自独立执行一次；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void differentKeysEachExecuteOnce() throws Exception {
        IdempotentExecutor<String> executor = executor();
        AtomicInteger sideEffects = new AtomicInteger();

        executor.execute("op-001", "sha256:same-request",
                () -> "receipt-" + sideEffects.incrementAndGet());
        executor.execute("op-002", "sha256:same-request",
                () -> "receipt-" + sideEffects.incrementAndGet());

        assertEquals(2, sideEffects.get());
    }

    // 场景：失败结果不缓存且同一操作可以再次尝试；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void failedResultIsNotCachedAndTheSameOperationMayBeAttemptedAgain() throws Exception {
        IdempotentExecutor<String> executor = executor();
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(IOException.class, () -> executor.execute("op-retry", "sha256:request",
                () -> {
                    attempts.incrementAndGet();
                    throw new IOException("failed before a result was committed");
                }));
        String recovered = executor.execute("op-retry", "sha256:request", () -> {
            attempts.incrementAndGet();
            return "committed";
        });

        assertEquals("committed", recovered);
        assertEquals(2, attempts.get());
    }

    // 场景：稳定业务键阻止网络重试或消息重放重复已提交副作用；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void networkRetryOrMessageReplayWithStableOperationKeyCannotRepeatCommittedEffect()
            throws Exception {
        IdempotentExecutor<Integer> executor = new IdempotentExecutor<>(
                new InMemoryIdempotencyStore<>());
        AtomicInteger chargedAmount = new AtomicInteger();

        int acknowledged = executor.execute("charge/order-42", "amount=100",
                () -> chargedAmount.addAndGet(100));
        int replayAcknowledged = executor.execute("charge/order-42", "amount=100",
                () -> chargedAmount.addAndGet(100));

        assertEquals(100, acknowledged);
        assertEquals(100, replayAcknowledged);
        assertEquals(100, chargedAmount.get());
    }

    // 场景：幂等执行器收到空或空白键以及空操作；行为：执行参数校验；预期：在访问存储前全部拒绝。
    @Test
    void rejectsInvalidExecutionArguments() {
        IdempotentExecutor<String> executor = executor();

        assertThrows(IllegalArgumentException.class,
                () -> executor.execute(null, "fingerprint", () -> "result"));
        assertThrows(IllegalArgumentException.class,
                () -> executor.execute(" ", "fingerprint", () -> "result"));
        assertThrows(IllegalArgumentException.class,
                () -> executor.execute("key", null, () -> "result"));
        assertThrows(IllegalArgumentException.class,
                () -> executor.execute("key", " ", () -> "result"));
        assertThrows(NullPointerException.class,
                () -> executor.execute("key", "fingerprint", null));
    }

    private static IdempotentExecutor<String> executor() {
        return new IdempotentExecutor<>(new InMemoryIdempotencyStore<>());
    }
}


