package com.pickagent.w3d1;

import com.openai.models.responses.ResponseFileSearchToolCall;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.ResponseReasoningItem;
import com.pickagent.w2.core.ToolRegistry;
import com.pickagent.w2.infrastructure.ReplayOrderTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseProtocolReplayTest {
    @Test
    void executesTheToolExactlyOnceAndReturnsTheFinalAnswer() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        ToolRegistry registry = registry(executions);

        var result = new ResponseProtocolReplay().run(
                firstOutput().items(),
                ignored -> finalOutput("订单 ORD-001 已发货。"),
                registry);

        assertEquals(1, executions.get());
        assertEquals("订单 ORD-001 已发货。", result.finalAnswer());
    }

    @Test
    void passesOriginalReasoningAndFunctionCallIntoTheSecondReplayRequest() throws Exception {
        FirstFixture first = firstOutput();
        AtomicReference<List<ResponseInputItem>> received = new AtomicReference<>();

        new ResponseProtocolReplay().run(first.items(), input -> {
            received.set(input);
            return finalOutput("done");
        }, registry(new AtomicInteger()));

        List<ResponseInputItem> input = received.get();
        assertEquals(3, input.size());
        assertSame(first.reasoning(), input.get(0).asReasoning());
        assertSame(first.call(), input.get(1).asFunctionCall());
        assertTrue(input.get(2).isFunctionCallOutput());
    }

    @Test
    void preservesCallIdThroughObservationAndStopsAfterOneFinalReplayTurn() throws Exception {
        FirstFixture first = firstOutput();
        AtomicInteger secondTurnCalls = new AtomicInteger();

        var result = new ResponseProtocolReplay().run(first.items(), input -> {
            secondTurnCalls.incrementAndGet();
            assertEquals(first.call().callId(), input.getLast().asFunctionCallOutput().callId());
            return finalOutput("final");
        }, registry(new AtomicInteger()));

        assertEquals(first.call().callId(), result.callId());
        assertEquals(1, secondTurnCalls.get());
        assertEquals("final", result.finalAnswer());
    }

    @Test
    void invalidFirstTurnProtocolCannotExecuteTheHandler() {
        AtomicInteger executions = new AtomicInteger();
        AtomicInteger secondTurns = new AtomicInteger();
        FirstFixture first = firstOutput();

        var failure = assertThrows(OpenAiResponseLedgerException.class,
                () -> new ResponseProtocolReplay().run(List.of(
                                ResponseOutputItem.ofFileSearchCall(fileSearchCall()),
                                ResponseOutputItem.ofFunctionCall(first.call())),
                        ignored -> {
                            secondTurns.incrementAndGet();
                            return finalOutput("must-not-run");
                        }, registry(executions)));

        assertEquals(OpenAiResponseLedgerException.Reason.UNKNOWN_OUTPUT_ITEM, failure.reason());
        assertEquals(0, executions.get(),
                "complete first-turn protocol validation must precede registry.execute");
        assertEquals(0, secondTurns.get());
    }

    @Test
    void unexpectedSecondTurnToolItemFailsClosed() {
        AtomicInteger executions = new AtomicInteger();

        var failure = assertThrows(OpenAiResponseLedgerException.class,
                () -> new ResponseProtocolReplay().run(
                        firstOutput().items(),
                        ignored -> List.of(
                                ResponseOutputItem.ofFileSearchCall(fileSearchCall()),
                                finalOutput("must-not-be-accepted").getFirst()),
                        registry(executions)));

        assertEquals(OpenAiResponseLedgerException.Reason.UNEXPECTED_FINAL_OUTPUT_ITEM,
                failure.reason());
        assertTrue(failure.getMessage().contains("file_search_call"));
        assertEquals(1, executions.get(), "the valid first turn executes exactly once");
    }

    private static ToolRegistry registry(AtomicInteger executions) {
        ReplayOrderTool tool = new ReplayOrderTool();
        return new ToolRegistry(List.of(new ToolRegistry.Registration(
                ReplayOrderTool.DEFINITION,
                arguments -> {
                    executions.incrementAndGet();
                    return tool.execute(arguments);
                })));
    }

    private static FirstFixture firstOutput() {
        ResponseReasoningItem reasoning = ResponseReasoningItem.builder()
                .id("rs_replay")
                .summary(List.of())
                .encryptedContent("opaque-replay-reasoning")
                .build();
        ResponseFunctionToolCall call = ResponseFunctionToolCall.builder()
                .id("fc_replay")
                .callId("call_replay")
                .name("lookup_order")
                .arguments("{\"orderId\":\"ORD-001\"}")
                .status(ResponseFunctionToolCall.Status.COMPLETED)
                .build();
        return new FirstFixture(reasoning, call, List.of(
                ResponseOutputItem.ofReasoning(reasoning),
                ResponseOutputItem.ofFunctionCall(call)));
    }

    private static List<ResponseOutputItem> finalOutput(String text) {
        ResponseOutputMessage message = ResponseOutputMessage.builder()
                .id("msg_final")
                .status(ResponseOutputMessage.Status.COMPLETED)
                .addContent(ResponseOutputText.builder()
                        .annotations(List.of())
                        .text(text)
                        .build())
                .build();
        return List.of(ResponseOutputItem.ofMessage(message));
    }

    private static ResponseFileSearchToolCall fileSearchCall() {
        return ResponseFileSearchToolCall.builder()
                .id("fs_unexpected")
                .queries(List.of("order"))
                .status(ResponseFileSearchToolCall.Status.COMPLETED)
                .build();
    }

    private record FirstFixture(
            ResponseReasoningItem reasoning,
            ResponseFunctionToolCall call,
            List<ResponseOutputItem> items) {
    }
}
