package com.pickagent.w2;

import com.pickagent.w2.reliability.FailureKind;
import com.pickagent.w2.reliability.IdempotentExecutor;
import com.pickagent.w2.reliability.InMemoryIdempotencyStore;
import com.pickagent.w2.reliability.RetryDecision;
import com.pickagent.w2.reliability.RetryPolicy;

import java.io.IOException;
import java.io.PrintStream;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs a deterministic, offline example that combines bounded retry decisions
 * with application-key idempotency without performing a real wait or network call.
 */
public final class RetryAndIdempotencyDemo {
    private static final String OPERATION_KEY = "submit-order/order-42";
    private static final String REQUEST_FINGERPRINT = "orderId=42&amount=100";

    /** Utility class; not instantiable. */
    private RetryAndIdempotencyDemo() {
    }

    /**
     * Executes the offline scenario and prints its trace.
     *
     * @param args ignored command-line arguments
     * @throws Exception if the deterministic fixture violates the expected flow
     */
    public static void main(String[] args) throws Exception {
        run(System.out);
    }

    static void run(PrintStream output) throws Exception {
        Objects.requireNonNull(output, "output");
        RetryPolicy retryPolicy = new RetryPolicy(
                3,
                Duration.ofSeconds(10),
                Duration.ofSeconds(1),
                Duration.ofSeconds(4),
                ignored -> Duration.ZERO);
        IdempotentExecutor<String> executor = new IdempotentExecutor<>(
                new InMemoryIdempotencyStore<>());
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger sideEffects = new AtomicInteger();

        try {
            execute(executor, attempts, sideEffects);
            throw new IllegalStateException("the first attempt was expected to fail");
        } catch (IOException expectedTemporaryFailure) {
            output.println("attempt=1 outcome=TEMPORARY_FAILURE kind=TRANSIENT_RATE_LIMIT");
            RetryDecision decision = retryPolicy.decide(
                    FailureKind.TRANSIENT_RATE_LIMIT,
                    1,
                    Duration.ZERO,
                    Optional.of(Duration.ofSeconds(2)));
            RetryDecision.RetryAfter retry = requireRetry(decision);
            output.println("retryDecision=RETRY_AFTER delay=" + retry.delay());
        }

        String success = execute(executor, attempts, sideEffects);
        output.println("attempt=2 outcome=SUCCESS result=" + success);

        String replay = executor.execute(OPERATION_KEY, REQUEST_FINGERPRINT, () -> {
            sideEffects.incrementAndGet();
            throw new AssertionError("a replay must not execute the side effect");
        });
        output.println("replay outcome=CACHED result=" + replay);

        if (sideEffects.get() != 1) {
            throw new IllegalStateException("side effect count must be exactly 1");
        }
        output.println("sideEffectCount=1 proof=PASS");
    }

    private static String execute(IdempotentExecutor<String> executor,
                                  AtomicInteger attempts,
                                  AtomicInteger sideEffects) throws Exception {
        return executor.execute(OPERATION_KEY, REQUEST_FINGERPRINT, () -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IOException("simulated temporary transport failure");
            }
            sideEffects.incrementAndGet();
            return "receipt-order-42";
        });
    }

    private static RetryDecision.RetryAfter requireRetry(RetryDecision decision) {
        if (decision instanceof RetryDecision.RetryAfter retry) {
            return retry;
        }
        throw new IllegalStateException("the temporary failure should be retryable: " + decision);
    }
}
