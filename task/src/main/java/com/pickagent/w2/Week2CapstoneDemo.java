package com.pickagent.w2;

import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import com.pickagent.w2.core.AgentDecision;
import com.pickagent.w2.core.ToolExecutionException;
import com.pickagent.w2.core.ToolRegistry;
import com.pickagent.w2.core.ToolResult;
import com.pickagent.w2.infrastructure.ReplayOrderTool;
import com.pickagent.w2.openai.OpenAiFunctionCallMapper;
import com.pickagent.w2.openai.OpenAiFunctionCallOutputMapper;
import com.pickagent.w2.openai.OpenAiFunctionToolMapper;
import com.pickagent.w2.reliability.FailureKind;
import com.pickagent.w2.reliability.IdempotentExecutor;
import com.pickagent.w2.reliability.InMemoryIdempotencyStore;
import com.pickagent.w2.reliability.RetryDecision;
import com.pickagent.w2.reliability.RetryPolicy;

import java.io.PrintStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Connects the existing W2 schema, protocol mapping, registry, retry and
 * idempotency components into one deterministic offline vertical slice.
 */
public final class Week2CapstoneDemo {
    private static final String OPERATION_KEY = "lookup-order/ORD-001";
    private static final String REQUEST_FINGERPRINT = "orderId=ORD-001";

    /** Utility class; not instantiable. */
    private Week2CapstoneDemo() {
    }

    /**
     * Runs the offline capstone and prints an observable responsibility trace.
     *
     * @param args ignored command-line arguments
     */
    public static void main(String[] args) {
        run(System.out);
    }

    static Evidence run(PrintStream output) {
        Objects.requireNonNull(output, "output");

        FunctionTool advertisedTool = new OpenAiFunctionToolMapper().map(ReplayOrderTool.DEFINITION);
        ResponseFunctionToolCall sdkCall = ResponseFunctionToolCall.builder()
                .id("fc_capstone_001")
                .callId("call_capstone_001")
                .name("lookup_order")
                .arguments("{\"orderId\":\"ORD-001\"}")
                .build();
        AgentDecision.ToolCall coreCall = new OpenAiFunctionCallMapper()
                .map(List.of(ResponseOutputItem.ofFunctionCall(sdkCall)));

        AtomicInteger handlerInvocations = new AtomicInteger();
        AtomicInteger sideEffects = new AtomicInteger();
        IdempotentExecutor<String> idempotentExecutor = new IdempotentExecutor<>(
                new InMemoryIdempotencyStore<>());
        ReplayOrderTool replayTool = new ReplayOrderTool();
        ToolRegistry registry = new ToolRegistry(List.of(new ToolRegistry.Registration(
                ReplayOrderTool.DEFINITION,
                arguments -> executeIdempotently(idempotentExecutor, replayTool, arguments,
                        handlerInvocations, sideEffects))));

        ToolResult firstResult = executeRegistry(registry, coreCall);
        ResponseInputItem.FunctionCallOutput sdkOutput =
                new OpenAiFunctionCallOutputMapper().map(firstResult);
        ToolResult replayResult = executeRegistry(registry, coreCall);

        RetryPolicy retryPolicy = new RetryPolicy(
                3, Duration.ofSeconds(10), Duration.ofSeconds(1), Duration.ofSeconds(4),
                ignored -> Duration.ofMillis(250));
        RetryDecision retryDecision = retryPolicy.decide(
                FailureKind.TRANSIENT_RATE_LIMIT,
                1,
                Duration.ZERO,
                Optional.of(Duration.ofSeconds(5)));

        Evidence evidence = new Evidence(advertisedTool, sdkCall, coreCall, firstResult,
                replayResult, sdkOutput, retryDecision, handlerInvocations.get(), sideEffects.get());
        verify(evidence);
        printTrace(output, evidence);
        return evidence;
    }

    private static String executeIdempotently(IdempotentExecutor<String> executor,
                                              ReplayOrderTool replayTool,
                                              Map<String, String> arguments,
                                              AtomicInteger handlerInvocations,
                                              AtomicInteger sideEffects)
            throws ToolExecutionException {
        handlerInvocations.incrementAndGet();
        try {
            return executor.execute(OPERATION_KEY, REQUEST_FINGERPRINT, () -> {
                sideEffects.incrementAndGet();
                return replayTool.execute(arguments);
            });
        } catch (RuntimeException programmingOrContractFailure) {
            throw programmingOrContractFailure;
        } catch (Exception expectedOperationFailure) {
            throw new ToolExecutionException("idempotent order lookup failed", expectedOperationFailure);
        }
    }

    private static ToolResult executeRegistry(ToolRegistry registry, AgentDecision.ToolCall call) {
        try {
            return registry.execute(call);
        } catch (ToolExecutionException unexpectedFixtureFailure) {
            throw new IllegalStateException("offline replay handler failed", unexpectedFixtureFailure);
        }
    }

    private static void verify(Evidence evidence) {
        String callId = evidence.sdkCall().callId();
        if (!callId.equals(evidence.coreCall().callId())
                || !callId.equals(evidence.firstResult().callId())
                || !callId.equals(evidence.sdkOutput().callId())) {
            throw new IllegalStateException("call_id changed during the vertical slice");
        }
        if (!evidence.firstResult().equals(evidence.replayResult())
                || evidence.handlerInvocations() != 2
                || evidence.sideEffects() != 1) {
            throw new IllegalStateException("idempotent replay invariant failed");
        }
        if (!(evidence.retryDecision() instanceof RetryDecision.RetryAfter retry)
                || !Duration.ofMillis(5250).equals(retry.delay())) {
            throw new IllegalStateException("Retry-After plus jitter invariant failed");
        }
    }

    private static void printTrace(PrintStream output, Evidence evidence) {
        output.println("chain=SDK call -> mapper -> core ToolCall -> Registry -> handler"
                + " -> ToolResult -> output mapper -> SDK output");
        output.println("schema.strict=" + evidence.advertisedTool().strict().orElse(false));
        output.println("inbound.call_id=" + evidence.sdkCall().callId());
        output.println("core.callId=" + evidence.coreCall().callId());
        output.println("toolResult.callId=" + evidence.firstResult().callId());
        output.println("outbound.call_id=" + evidence.sdkOutput().callId());
        output.println("retry.delay="
                + ((RetryDecision.RetryAfter) evidence.retryDecision()).delay());
        output.println("replay.handlerInvocations=" + evidence.handlerInvocations());
        output.println("replay.sideEffects=" + evidence.sideEffects());
        output.println("capstone.proof=PASS");
    }

    record Evidence(FunctionTool advertisedTool,
                    ResponseFunctionToolCall sdkCall,
                    AgentDecision.ToolCall coreCall,
                    ToolResult firstResult,
                    ToolResult replayResult,
                    ResponseInputItem.FunctionCallOutput sdkOutput,
                    RetryDecision retryDecision,
                    int handlerInvocations,
                    int sideEffects) {
    }
}
