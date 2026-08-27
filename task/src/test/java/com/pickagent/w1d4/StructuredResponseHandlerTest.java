package com.pickagent.w1d4;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class StructuredResponseHandlerTest {

    @Test
    void completedOutputTextCanReachSuccessfulDecodeBranch() {
        AtomicInteger decodeCalls = new AtomicInteger();
        StructuredResponseHandler handler = handlerWithCountingDecoder(decodeCalls);

        StructuredResponseHandler.HandlingResult result = handler.handle(
                StructuredResponseHandler.ResponseSnapshot.completed("valid-json-placeholder"));

        StructuredResponseHandler.HandlingResult.EventAccepted accepted = assertInstanceOf(
                StructuredResponseHandler.HandlingResult.EventAccepted.class,
                result
        );
        assertEquals(new Event("Demo", "2026-08-27", List.of("Alice")), accepted.event());
        assertEquals(1, decodeCalls.get());
    }

    @Test
    void refusalNeverCallsDecoderOrReachesSuccessBranch() {
        AtomicInteger decodeCalls = new AtomicInteger();
        StructuredResponseHandler handler = handlerWithCountingDecoder(decodeCalls);

        StructuredResponseHandler.HandlingResult result = handler.handle(
                StructuredResponseHandler.ResponseSnapshot.refusal("Safety refusal"));

        StructuredResponseHandler.HandlingResult.Refused refused = assertInstanceOf(
                StructuredResponseHandler.HandlingResult.Refused.class,
                result
        );
        assertEquals("Safety refusal", refused.reason());
        assertEquals(0, decodeCalls.get());
    }

    @Test
    void incompletePartialJsonNeverCallsDecoderOrReachesSuccessBranch() {
        AtomicInteger decodeCalls = new AtomicInteger();
        StructuredResponseHandler handler = handlerWithCountingDecoder(decodeCalls);

        StructuredResponseHandler.HandlingResult result = handler.handle(
                StructuredResponseHandler.ResponseSnapshot.incomplete(
                        "max_output_tokens",
                        "{\"name\":\"partial\",\"date\":\"2026-08-27\",\"participants\":[]}"));

        StructuredResponseHandler.HandlingResult.Incomplete incomplete = assertInstanceOf(
                StructuredResponseHandler.HandlingResult.Incomplete.class,
                result
        );
        assertEquals("max_output_tokens", incomplete.reason());
        assertEquals(0, decodeCalls.get());
    }

    @Test
    void completedInvalidOutputReturnsDecoderFailure() {
        StructuredResponseHandler handler = new StructuredResponseHandler();

        StructuredResponseHandler.HandlingResult result = handler.handle(
                StructuredResponseHandler.ResponseSnapshot.completed("""
                        {"name":"Demo","date":"2026-08-27","participants":[],"extra":true}
                        """));

        StructuredResponseHandler.HandlingResult.InvalidOutput invalid = assertInstanceOf(
                StructuredResponseHandler.HandlingResult.InvalidOutput.class,
                result
        );
        assertEquals(DecodeResult.ErrorCode.EXTRA_FIELDS, invalid.failure().code());
    }

    private static StructuredResponseHandler handlerWithCountingDecoder(AtomicInteger calls) {
        return new StructuredResponseHandler(text -> {
            calls.incrementAndGet();
            return new DecodeResult.Success(new Event("Demo", "2026-08-27", List.of("Alice")));
        });
    }
}
