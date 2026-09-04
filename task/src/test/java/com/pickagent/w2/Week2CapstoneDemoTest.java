package com.pickagent.w2;

import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseOutputItem;
import com.pickagent.w2.openai.OpenAiFunctionCallMapper;
import com.pickagent.w2.openai.OpenAiFunctionCallMappingException;
import com.pickagent.w2.reliability.FailureKind;
import com.pickagent.w2.reliability.IdempotentExecutor;
import com.pickagent.w2.reliability.InMemoryIdempotencyStore;
import com.pickagent.w2.reliability.RetryDecision;
import com.pickagent.w2.reliability.RetryPolicy;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.io.PrintStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Week2CapstoneDemoTest {
    @Test
    void strictSchemaIsPreservedAtTheSdkBoundary() {
        var evidence = run();
        var schema = evidence.advertisedTool().parameters().orElseThrow()._additionalProperties();

        assertTrue(evidence.advertisedTool().strict().orElse(false));
        assertEquals("object", schema.get("type").convert(String.class));
        assertEquals(Map.of("orderId", Map.of("type", "string")),
                schema.get("properties").convert(Object.class));
        assertEquals(List.of("orderId"), schema.get("required").convert(Object.class));
        assertFalse(schema.get("additionalProperties").convert(Boolean.class));
    }

    @Test
    void callIdSurvivesTheCompleteSdkCoreSdkRoundTrip() {
        var evidence = run();

        assertEquals(evidence.sdkCall().callId(), evidence.coreCall().callId());
        assertEquals(evidence.sdkCall().callId(), evidence.firstResult().callId());
        assertEquals(evidence.sdkCall().callId(), evidence.sdkOutput().callId());
    }

    @Test
    void retryAfterIsTheMinimumAndReceivesInjectedJitter() {
        var retry = assertInstanceOf(RetryDecision.RetryAfter.class, run().retryDecision());

        assertEquals(Duration.ofMillis(5250), retry.delay());
    }

    @Test
    void permanentFailureStopsWithoutConsultingJitter() {
        RetryPolicy policy = new RetryPolicy(
                3, Duration.ofSeconds(10), Duration.ofSeconds(1), Duration.ofSeconds(4),
                ignored -> {
                    throw new AssertionError("permanent failures must not calculate jitter");
                });

        var stop = assertInstanceOf(RetryDecision.Stop.class,
                policy.decide(FailureKind.AUTHENTICATION, 1,
                        Duration.ZERO, Optional.of(Duration.ofSeconds(5))));

        assertEquals("failure is not retryable: AUTHENTICATION", stop.reason());
    }

    @Test
    void sameOperationKeyAndFingerprintExecuteTheSideEffectOnlyOnce() {
        var evidence = run();

        assertEquals(2, evidence.handlerInvocations(), "the replay still reaches the handler boundary");
        assertEquals(1, evidence.sideEffects(), "the idempotency executor suppresses the second effect");
        assertEquals(evidence.firstResult(), evidence.replayResult());
    }

    @Test
    void sameOperationKeyWithDifferentFingerprintIsRejected() throws Exception {
        IdempotentExecutor<String> executor = new IdempotentExecutor<>(
                new InMemoryIdempotencyStore<>());
        AtomicInteger sideEffects = new AtomicInteger();
        executor.execute("operation-1", "request-a", () -> {
            sideEffects.incrementAndGet();
            return "first";
        });

        var conflict = assertThrows(IdempotentExecutor.IdempotencyConflictException.class,
                () -> executor.execute("operation-1", "request-b", () -> {
                    sideEffects.incrementAndGet();
                    return "must-not-run";
                }));

        assertTrue(conflict.getMessage().contains("different request"));
        assertEquals(1, sideEffects.get());
    }

    @Test
    void failedOperationIsNotCachedAndCanSucceedOnTheNextAttempt() throws Exception {
        IdempotentExecutor<String> executor = new IdempotentExecutor<>(
                new InMemoryIdempotencyStore<>());
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(IllegalStateException.class,
                () -> executor.execute("operation-retry", "request", () -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("temporary fixture failure");
                }));
        String result = executor.execute("operation-retry", "request", () -> {
            attempts.incrementAndGet();
            return "recovered";
        });

        assertEquals("recovered", result);
        assertEquals(2, attempts.get());
    }

    @Test
    void multipleFunctionCallsAreRejectedInsteadOfSilentlyTruncated() {
        var first = sdkCall("call-1");
        var second = sdkCall("call-2");

        var failure = assertThrows(OpenAiFunctionCallMappingException.class,
                () -> new OpenAiFunctionCallMapper().map(List.of(
                        ResponseOutputItem.ofFunctionCall(first),
                        ResponseOutputItem.ofFunctionCall(second))));

        assertEquals(OpenAiFunctionCallMappingException.Reason.MULTIPLE_FUNCTION_CALLS,
                failure.reason());
    }

    private static Week2CapstoneDemo.Evidence run() {
        return Week2CapstoneDemo.run(new PrintStream(OutputStream.nullOutputStream()));
    }

    private static ResponseFunctionToolCall sdkCall(String callId) {
        return ResponseFunctionToolCall.builder()
                .id("fc-" + callId)
                .callId(callId)
                .name("lookup_order")
                .arguments("{\"orderId\":\"ORD-001\"}")
                .build();
    }
}
