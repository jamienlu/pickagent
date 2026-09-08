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
     * Validates and converts a response output history without executing a tool
     * or performing any other side effect.
     *
     * @param outputItems heterogeneous output items from the preceding response
     * @return immutable prepared protocol history
     * @throws NullPointerException if the list or one of its elements is null
     * @throws OpenAiResponseLedgerException if the history cannot be continued safely
     */
    public PreparedLedger prepare(List<ResponseOutputItem> outputItems) {
        Objects.requireNonNull(outputItems, "outputItems");

        List<ResponseOutputItem> outputSnapshot = List.copyOf(outputItems);
        List<ResponseInputItem> protocolItems = new ArrayList<>(outputSnapshot.size());
        List<ResponseFunctionToolCall> calls = new ArrayList<>();

        for (ResponseOutputItem item : outputSnapshot) {
            if (item.isReasoning()) {
                protocolItems.add(ResponseInputItem.ofReasoning(item.asReasoning()));
            } else if (item.isMessage()) {
                protocolItems.add(ResponseInputItem.ofResponseOutputMessage(item.asMessage()));
            } else if (item.isFunctionCall()) {
                ResponseFunctionToolCall call = item.asFunctionCall();
                calls.add(call);
                protocolItems.add(ResponseInputItem.ofFunctionCall(call));
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

        return new PreparedLedger(protocolItems, calls.getFirst());
    }

    /**
     * Appends an executed result to a previously validated protocol history.
     *
     * @param prepared side-effect-free result of {@link #prepare(List)}
     * @param toolResult locally executed tool result
     * @return immutable continuation-input snapshot
     * @throws NullPointerException if an argument is null
     * @throws OpenAiResponseLedgerException if the result targets another call
     */
    public List<ResponseInputItem> append(PreparedLedger prepared, ToolResult toolResult) {
        Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(toolResult, "toolResult");

        ResponseFunctionToolCall call = prepared.functionCall();
        if (!call.callId().equals(toolResult.callId())) {
            throw new OpenAiResponseLedgerException(
                    OpenAiResponseLedgerException.Reason.CALL_ID_MISMATCH,
                    "tool result callId '" + toolResult.callId()
                            + "' does not match function_call call_id '" + call.callId() + "'");
        }

        List<ResponseInputItem> continuation = new ArrayList<>(prepared.protocolItems());
        continuation.add(ResponseInputItem.ofFunctionCallOutput(
                new OpenAiFunctionCallOutputMapper().map(toolResult)));
        return List.copyOf(continuation);
    }

    static String itemType(ResponseOutputItem item) {
        if (item.isReasoning()) {
            return "reasoning";
        }
        if (item.isMessage()) {
            return "assistant/message";
        }
        if (item.isFunctionCall()) {
            return "function_call";
        }
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

    /**
     * Immutable, fully validated first-turn protocol history. Instances can
     * only be created by {@link OpenAiResponseLedger#prepare(List)}.
     */
    public static final class PreparedLedger {
        private final List<ResponseInputItem> protocolItems;
        private final ResponseFunctionToolCall functionCall;

        private PreparedLedger(
                List<ResponseInputItem> protocolItems,
                ResponseFunctionToolCall functionCall) {
            this.protocolItems = List.copyOf(protocolItems);
            this.functionCall = Objects.requireNonNull(functionCall, "functionCall");
        }

        /**
         * Returns the immutable input items in their original order.
         *
         * @return validated protocol items, without a tool result
         */
        public List<ResponseInputItem> protocolItems() {
            return protocolItems;
        }

        /**
         * Returns the single function call validated by the ledger.
         *
         * @return original SDK function-call object
         */
        public ResponseFunctionToolCall functionCall() {
            return functionCall;
        }
    }
}
