package com.pickagent.w1d5.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerateEventUseCaseTest {

    @Test
    void completedOutputIsParsedIntoGeneratedEvent() {
        FakeModelGateway gateway = new FakeModelGateway(new ModelResult.Completed("event-json"));
        AtomicReference<String> parsedContent = new AtomicReference<>();
        EventData expected = new EventData("AI Meetup", "Friday", List.of("Alice", "Bob"));
        EventParser parser = content -> {
            parsedContent.set(content);
            return new EventParser.ParseResult.Parsed(expected);
        };

        GenerateEventResult result = new GenerateEventUseCase(gateway, parser)
                .generate("Alice and Bob attend an AI meetup on Friday.");

        GenerateEventResult.Generated generated = assertInstanceOf(
                GenerateEventResult.Generated.class,
                result
        );
        assertEquals(expected, generated.event());
        assertEquals("event-json", parsedContent.get());
        assertEquals(1, gateway.callCount());
        assertTrue(gateway.lastCommand().prompt().contains("Alice and Bob"));
    }

    @Test
    void refusalNeverEntersBusinessParsing() {
        FakeModelGateway gateway = new FakeModelGateway(new ModelResult.Refused("safety policy"));
        AtomicInteger parserCalls = new AtomicInteger();
        EventParser parser = content -> {
            parserCalls.incrementAndGet();
            return new EventParser.ParseResult.Invalid("must not be called");
        };

        GenerateEventResult result = new GenerateEventUseCase(gateway, parser)
                .generate("Extract this event.");

        GenerateEventResult.Refused refused = assertInstanceOf(
                GenerateEventResult.Refused.class,
                result
        );
        assertEquals("safety policy", refused.reason());
        assertEquals(0, parserCalls.get());
        assertEquals(1, gateway.callCount());
    }

    @Test
    void incompletePreservesProviderNeutralReason() {
        FakeModelGateway gateway = new FakeModelGateway(
                new ModelResult.Incomplete("max_output_tokens"));
        AtomicInteger parserCalls = new AtomicInteger();
        EventParser parser = content -> {
            parserCalls.incrementAndGet();
            return new EventParser.ParseResult.Invalid("must not be called");
        };

        GenerateEventResult result = new GenerateEventUseCase(gateway, parser)
                .generate("Extract this event.");

        GenerateEventResult.Incomplete incomplete = assertInstanceOf(
                GenerateEventResult.Incomplete.class,
                result
        );
        assertEquals("max_output_tokens", incomplete.reason());
        assertEquals(0, parserCalls.get());
    }

    @Test
    void gatewayFailureBecomesExplicitApplicationFailureWithoutRetry() {
        FakeModelGateway gateway = new FakeModelGateway(
                new ModelResult.Failed(ModelResult.FailureKind.TRANSPORT, "connection timed out"));
        AtomicInteger parserCalls = new AtomicInteger();
        EventParser parser = content -> {
            parserCalls.incrementAndGet();
            return new EventParser.ParseResult.Invalid("must not be called");
        };

        GenerateEventResult result = new GenerateEventUseCase(gateway, parser)
                .generate("Extract this event.");

        GenerateEventResult.Failed failed = assertInstanceOf(
                GenerateEventResult.Failed.class,
                result
        );
        assertEquals(GenerateEventResult.FailureKind.GATEWAY_TRANSPORT, failed.kind());
        assertEquals("connection timed out", failed.reason());
        assertEquals(0, parserCalls.get());
        assertEquals(1, gateway.callCount());
    }

    @Test
    void unexpectedGatewayProgrammingErrorIsNotDisguisedAsTransportFailure() {
        ModelGateway brokenGateway = command -> {
            throw new IllegalStateException("adapter mapping bug");
        };
        AtomicInteger parserCalls = new AtomicInteger();
        EventParser parser = content -> {
            parserCalls.incrementAndGet();
            return new EventParser.ParseResult.Invalid("must not be called");
        };
        GenerateEventUseCase useCase = new GenerateEventUseCase(brokenGateway, parser);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> useCase.generate("Extract this event.")
        );

        assertEquals("adapter mapping bug", exception.getMessage());
        assertEquals(0, parserCalls.get());
    }

    @Test
    void invalidCompletedOutputBecomesBusinessValidationFailure() {
        FakeModelGateway gateway = new FakeModelGateway(new ModelResult.Completed("invalid-json"));
        EventParser parser = content -> new EventParser.ParseResult.Invalid("missing field: date");

        GenerateEventResult result = new GenerateEventUseCase(gateway, parser)
                .generate("Extract this event.");

        GenerateEventResult.Failed failed = assertInstanceOf(
                GenerateEventResult.Failed.class,
                result
        );
        assertEquals(GenerateEventResult.FailureKind.INVALID_MODEL_OUTPUT, failed.kind());
        assertEquals("missing field: date", failed.reason());
    }
}
