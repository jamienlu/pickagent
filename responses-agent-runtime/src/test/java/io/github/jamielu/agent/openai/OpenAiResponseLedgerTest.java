package io.github.jamielu.agent.openai;

import com.openai.models.responses.ResponseFileSearchToolCall;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.ResponseReasoningItem;
import io.github.jamielu.agent.api.ToolResult;
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

        OpenAiResponseLedger.PreparedLedger prepared = ledger.prepare(List.of(
                ResponseOutputItem.ofReasoning(reasoning),
                ResponseOutputItem.ofMessage(message),
                ResponseOutputItem.ofFunctionCall(call)));
        List<ResponseInputItem> input = ledger.append(
                prepared, new ToolResult("call_001", "SHIPPED"));

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
                () -> ledger.prepare(
                        List.of(ResponseOutputItem.ofReasoning(reasoning()))));

        assertEquals(OpenAiResponseLedgerException.Reason.NO_FUNCTION_CALL, failure.reason());
    }

    @Test
    void multipleFunctionCallsAndOutputsPreserveResponseOrder() {
        ResponseFunctionToolCall first = call("call_001");
        ResponseFunctionToolCall second = call("call_002");
        OpenAiResponseLedger.PreparedLedger prepared = ledger.prepare(List.of(
                ResponseOutputItem.ofReasoning(reasoning()),
                ResponseOutputItem.ofFunctionCall(first),
                ResponseOutputItem.ofFunctionCall(second)));

        List<ResponseInputItem> continuation = ledger.appendAll(prepared, List.of(
                new ToolResult("call_001", "first"),
                new ToolResult("call_002", "second")));

        assertEquals(5, continuation.size());
        assertSame(first, continuation.get(1).asFunctionCall());
        assertSame(second, continuation.get(2).asFunctionCall());
        assertEquals("call_001", continuation.get(3).asFunctionCallOutput().callId());
        assertEquals("call_002", continuation.get(4).asFunctionCallOutput().callId());
        assertEquals(List.of(first, second), prepared.functionCalls());
    }

    @Test
    void duplicateFunctionCallIdFailsDuringPreparation() {
        var failure = assertThrows(OpenAiResponseLedgerException.class,
                () -> ledger.prepare(List.of(
                        ResponseOutputItem.ofFunctionCall(call("call_same")),
                        ResponseOutputItem.ofFunctionCall(call("call_same")))));

        assertEquals(OpenAiResponseLedgerException.Reason.DUPLICATE_CALL_ID,
                failure.reason());
    }

    @Test
    void incompleteResultBatchCannotBuildContinuation() {
        OpenAiResponseLedger.PreparedLedger prepared = ledger.prepare(List.of(
                ResponseOutputItem.ofFunctionCall(call("call_001")),
                ResponseOutputItem.ofFunctionCall(call("call_002"))));

        var failure = assertThrows(OpenAiResponseLedgerException.class,
                () -> ledger.appendAll(prepared,
                        List.of(new ToolResult("call_001", "only-one"))));

        assertEquals(OpenAiResponseLedgerException.Reason.RESULT_COUNT_MISMATCH,
                failure.reason());
    }

    @Test
    void reorderedResultsCannotBuildContinuation() {
        OpenAiResponseLedger.PreparedLedger prepared = ledger.prepare(List.of(
                ResponseOutputItem.ofFunctionCall(call("call_001")),
                ResponseOutputItem.ofFunctionCall(call("call_002"))));

        var failure = assertThrows(OpenAiResponseLedgerException.class,
                () -> ledger.appendAll(prepared, List.of(
                        new ToolResult("call_002", "second"),
                        new ToolResult("call_001", "first"))));

        assertEquals(OpenAiResponseLedgerException.Reason.CALL_ID_MISMATCH,
                failure.reason());
    }

    @Test
    void mismatchedCallIdFailsBeforeAppendingToolOutput() {
        OpenAiResponseLedger.PreparedLedger prepared = ledger.prepare(
                List.of(ResponseOutputItem.ofFunctionCall(call("call_expected"))));

        var failure = assertThrows(OpenAiResponseLedgerException.class,
                () -> ledger.append(
                        prepared, new ToolResult("call_other", "must-not-append")));

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
                () -> ledger.prepare(List.of(
                        ResponseOutputItem.ofFileSearchCall(unsupported),
                        ResponseOutputItem.ofFunctionCall(call("call_001")))));

        assertEquals(OpenAiResponseLedgerException.Reason.UNKNOWN_OUTPUT_ITEM, failure.reason());
        assertTrue(failure.getMessage().contains("file_search_call"));
    }

    @Test
    void returnedSnapshotCannotBeMutatedAndDoesNotTrackSourceListChanges() {
        List<ResponseOutputItem> source = new ArrayList<>();
        source.add(ResponseOutputItem.ofFunctionCall(call("call_001")));

        OpenAiResponseLedger.PreparedLedger prepared = ledger.prepare(source);
        source.clear();
        List<ResponseInputItem> snapshot = ledger.append(
                prepared, new ToolResult("call_001", "SHIPPED"));

        assertEquals(2, snapshot.size());
        assertFalse(snapshot.isEmpty());
        assertEquals(1, prepared.protocolItems().size());
        assertThrows(UnsupportedOperationException.class,
                () -> prepared.protocolItems().clear());
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


