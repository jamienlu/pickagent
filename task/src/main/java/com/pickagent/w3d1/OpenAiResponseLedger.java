package com.pickagent.w3d1;

import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import com.pickagent.w2.core.ToolResult;
import com.pickagent.w2.openai.OpenAiFunctionCallOutputMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Preserves the OpenAI Responses protocol items needed to continue one serial
 * reasoning-model tool call.
 *
 * <p>The ledger wraps the original SDK reasoning, assistant-message and
 * function-call objects as input items. It does not rebuild their fields. This
 * preserves opaque reasoning content, message metadata, provider extensions,
 * ordering and the original {@code call_id}.</p>
 */
public final class OpenAiResponseLedger {
    /** Creates a stateless protocol ledger. */
    public OpenAiResponseLedger() {
    }

    /**
     * Converts a response output history to continuation input and appends the
     * matching tool result at the end.
     *
     * @param outputItems heterogeneous output items from the preceding response
     * @param toolResult locally executed tool result
     * @return immutable continuation-input snapshot
     * @throws NullPointerException if an argument or list element is null
     * @throws OpenAiResponseLedgerException if the history cannot be continued safely
     */
    public List<ResponseInputItem> appendToolResult(
            List<ResponseOutputItem> outputItems,
            ToolResult toolResult) {
        Objects.requireNonNull(outputItems, "outputItems");
        Objects.requireNonNull(toolResult, "toolResult");

        List<ResponseOutputItem> outputSnapshot = List.copyOf(outputItems);
        List<ResponseInputItem> continuation = new ArrayList<>(outputSnapshot.size() + 1);
        List<ResponseFunctionToolCall> calls = new ArrayList<>();

        for (ResponseOutputItem item : outputSnapshot) {
            if (item.isReasoning()) {
                continuation.add(ResponseInputItem.ofReasoning(item.asReasoning()));
            } else if (item.isMessage()) {
                continuation.add(ResponseInputItem.ofResponseOutputMessage(item.asMessage()));
            } else if (item.isFunctionCall()) {
                ResponseFunctionToolCall call = item.asFunctionCall();
                calls.add(call);
                continuation.add(ResponseInputItem.ofFunctionCall(call));
            } else {
                throw new OpenAiResponseLedgerException(
                        OpenAiResponseLedgerException.Reason.UNKNOWN_OUTPUT_ITEM,
                        "unsupported response output item: " + itemType(item));
            }
        }

        if (calls.isEmpty()) {
            throw new OpenAiResponseLedgerException(
                    OpenAiResponseLedgerException.Reason.NO_FUNCTION_CALL,
                    "expected exactly one function_call but found 0");
        }
        if (calls.size() > 1) {
            throw new OpenAiResponseLedgerException(
                    OpenAiResponseLedgerException.Reason.MULTIPLE_FUNCTION_CALLS,
                    "expected exactly one function_call but found " + calls.size());
        }

        ResponseFunctionToolCall call = calls.getFirst();
        if (!call.callId().equals(toolResult.callId())) {
            throw new OpenAiResponseLedgerException(
                    OpenAiResponseLedgerException.Reason.CALL_ID_MISMATCH,
                    "tool result callId '" + toolResult.callId()
                            + "' does not match function_call call_id '" + call.callId() + "'");
        }

        continuation.add(ResponseInputItem.ofFunctionCallOutput(
                new OpenAiFunctionCallOutputMapper().map(toolResult)));
        return List.copyOf(continuation);
    }

    private static String itemType(ResponseOutputItem item) {
        if (item.isFileSearchCall()) {
            return "file_search_call";
        }
        if (item.isWebSearchCall()) {
            return "web_search_call";
        }
        if (item.isComputerCall()) {
            return "computer_call";
        }
        return "unknown";
    }
}
