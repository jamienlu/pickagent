package com.pickagent.w3d1;

import com.openai.models.responses.ResponseFileSearchToolCall;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.ResponseReasoningItem;
import com.pickagent.w2.core.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiResponseLedgerTest {
    private final OpenAiResponseLedger ledger = new OpenAiResponseLedger();

    @Test
    void preservesReasoningMessageAndFunctionCallOrderUsingOriginalSdkObjects() {
        ResponseReasoningItem reasoning = reasoning();
        ResponseOutputMessage message = assistantMessage("I will look up the order.");
        ResponseFunctionToolCall call = call("call_001");

        List<ResponseInputItem> input = ledger.appendToolResult(List.of(
                ResponseOutputItem.ofReasoning(reasoning),
                ResponseOutputItem.ofMessage(message),
                ResponseOutputItem.ofFunctionCall(call)),
                new ToolResult("call_001", "SHIPPED"));

        assertEquals(4, input.size());
        assertTrue(input.get(0).isReasoning());
        assertTrue(input.get(1).isResponseOutputMessage());
        assertTrue(input.get(2).isFunctionCall());
        assertTrue(input.get(3).isFunctionCallOutput());
        assertSame(reasoning, input.get(0).asReasoning());
        assertSame(message, input.get(1).asResponseOutputMessage());
        assertSame(call, input.get(2).asFunctionCall());
        assertEquals("call_001", input.get(3).asFunctionCallOutput().callId());
    }

    @Test
    void zeroFunctionCallsFailFast() {
        var failure = assertThrows(OpenAiResponseLedgerException.class,
                () -> ledger.appendToolResult(
                        List.of(ResponseOutputItem.ofReasoning(reasoning())),
                        new ToolResult("call_001", "unused")));

        assertEquals(OpenAiResponseLedgerException.Reason.NO_FUNCTION_CALL, failure.reason());
    }

    @Test
    void multipleFunctionCallsFailFast() {
        var failure = assertThrows(OpenAiResponseLedgerException.class,
                () -> ledger.appendToolResult(List.of(
                                ResponseOutputItem.ofFunctionCall(call("call_001")),
                                ResponseOutputItem.ofFunctionCall(call("call_002"))),
                        new ToolResult("call_001", "unused")));

        assertEquals(OpenAiResponseLedgerException.Reason.MULTIPLE_FUNCTION_CALLS, failure.reason());
    }

    @Test
    void mismatchedCallIdFailsBeforeAppendingToolOutput() {
        var failure = assertThrows(OpenAiResponseLedgerException.class,
                () -> ledger.appendToolResult(
                        List.of(ResponseOutputItem.ofFunctionCall(call("call_expected"))),
                        new ToolResult("call_other", "must-not-append")));

        assertEquals(OpenAiResponseLedgerException.Reason.CALL_ID_MISMATCH, failure.reason());
        assertTrue(failure.getMessage().contains("call_expected"));
        assertTrue(failure.getMessage().contains("call_other"));
    }

    @Test
    void unknownOutputItemFailsFastInsteadOfBeingSilentlyDropped() {
        ResponseFileSearchToolCall unsupported = ResponseFileSearchToolCall.builder()
                .id("fs_001")
                .queries(List.of("order"))
                .status(ResponseFileSearchToolCall.Status.COMPLETED)
                .build();

        var failure = assertThrows(OpenAiResponseLedgerException.class,
                () -> ledger.appendToolResult(List.of(
                                ResponseOutputItem.ofFileSearchCall(unsupported),
                                ResponseOutputItem.ofFunctionCall(call("call_001"))),
                        new ToolResult("call_001", "unused")));

        assertEquals(OpenAiResponseLedgerException.Reason.UNKNOWN_OUTPUT_ITEM, failure.reason());
        assertTrue(failure.getMessage().contains("file_search_call"));
    }

    @Test
    void returnedSnapshotCannotBeMutatedAndDoesNotTrackSourceListChanges() {
        List<ResponseOutputItem> source = new ArrayList<>();
        source.add(ResponseOutputItem.ofFunctionCall(call("call_001")));

        List<ResponseInputItem> snapshot = ledger.appendToolResult(
                source, new ToolResult("call_001", "SHIPPED"));
        source.clear();

        assertEquals(2, snapshot.size());
        assertFalse(snapshot.isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.add(ResponseInputItem.ofReasoning(reasoning())));
    }

    private static ResponseReasoningItem reasoning() {
        return ResponseReasoningItem.builder()
                .id("rs_001")
                .summary(List.of())
                .encryptedContent("opaque-reasoning")
                .build();
    }

    private static ResponseOutputMessage assistantMessage(String text) {
        return ResponseOutputMessage.builder()
                .id("msg_001")
                .status(ResponseOutputMessage.Status.COMPLETED)
                .addContent(ResponseOutputText.builder()
                        .annotations(List.of())
                        .text(text)
                        .build())
                .build();
    }

    private static ResponseFunctionToolCall call(String callId) {
        return ResponseFunctionToolCall.builder()
                .id("fc_" + callId)
                .callId(callId)
                .name("lookup_order")
                .arguments("{\"orderId\":\"ORD-001\"}")
                .status(ResponseFunctionToolCall.Status.COMPLETED)
                .build();
    }
}
