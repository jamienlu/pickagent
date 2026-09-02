package com.pickagent.w2;

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

import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Offline SDK-to-core-to-SDK call-id round-trip demonstration. */
public final class OpenAiToolRoundTripDemo {
    private OpenAiToolRoundTripDemo() {
    }

    /**
     * Runs the deterministic demonstration.
     *
     * @param args ignored command-line arguments
     */
    public static void main(String[] args) {
        run(System.out);
    }

    /**
     * Executes one replay handler and prints every correlation boundary.
     *
     * @param out destination for deterministic evidence
     */
    public static void run(PrintStream out) {
        ResponseFunctionToolCall sdkCall = ResponseFunctionToolCall.builder()
                .id("fc_order_001")
                .callId("call_order_001")
                .name("lookup_order")
                .arguments("{\"orderId\":\"ORD-001\"}")
                .build();

        AgentDecision.ToolCall coreCall = new OpenAiFunctionCallMapper()
                .map(List.of(ResponseOutputItem.ofFunctionCall(sdkCall)));
        AtomicInteger handlerExecutions = new AtomicInteger();
        ReplayOrderTool replay = new ReplayOrderTool();
        ToolRegistry registry = new ToolRegistry(List.of(new ToolRegistry.Registration(
                ReplayOrderTool.DEFINITION,
                arguments -> {
                    handlerExecutions.incrementAndGet();
                    return replay.execute(arguments);
                })));
        ToolResult coreResult;
        try {
            coreResult = registry.execute(coreCall);
        } catch (ToolExecutionException unexpectedFixtureFailure) {
            throw new IllegalStateException("offline replay handler failed", unexpectedFixtureFailure);
        }
        ResponseInputItem.FunctionCallOutput sdkOutput = new OpenAiFunctionCallOutputMapper().map(coreResult);

        if (!sdkCall.callId().equals(coreCall.callId())
                || !coreCall.callId().equals(coreResult.callId())
                || !coreResult.callId().equals(sdkOutput.callId())) {
            throw new IllegalStateException("call_id changed during SDK/core round trip");
        }
        if (handlerExecutions.get() != 1) {
            throw new IllegalStateException("round trip must execute exactly one handler");
        }

        out.println("inbound.sdk.call_id=" + sdkCall.callId());
        out.println("core.toolCall.callId=" + coreCall.callId());
        out.println("core.toolResult.callId=" + coreResult.callId());
        out.println("core.toolResult.output=" + coreResult.output());
        out.println("outbound.sdk.call_id=" + sdkOutput.callId());
        out.println("outbound.sdk.output=" + sdkOutput.output().asString());
        out.println("handlerExecutions=" + handlerExecutions.get());
        out.println("roundTrip.callIdMatch=true");
    }
}
