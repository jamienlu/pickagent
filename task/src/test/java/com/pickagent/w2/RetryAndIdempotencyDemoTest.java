package com.pickagent.w2;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetryAndIdempotencyDemoTest {
    @Test
    void demoRetriesOnceThenReplaysWithoutRepeatingTheSideEffect() throws Exception {
        var bytes = new ByteArrayOutputStream();

        RetryAndIdempotencyDemo.run(new PrintStream(bytes, true, StandardCharsets.UTF_8));

        assertEquals(String.join(System.lineSeparator(),
                "attempt=1 outcome=TEMPORARY_FAILURE kind=TRANSIENT_RATE_LIMIT",
                "retryDecision=RETRY_AFTER delay=PT2S",
                "attempt=2 outcome=SUCCESS result=receipt-order-42",
                "replay outcome=CACHED result=receipt-order-42",
                "sideEffectCount=1 proof=PASS",
                ""), bytes.toString(StandardCharsets.UTF_8));
    }
}
