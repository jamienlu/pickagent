package com.pickagent.w2.reliability;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdempotentExecutorTest {
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

    private static IdempotentExecutor<String> executor() {
        return new IdempotentExecutor<>(new InMemoryIdempotencyStore<>());
    }
}
