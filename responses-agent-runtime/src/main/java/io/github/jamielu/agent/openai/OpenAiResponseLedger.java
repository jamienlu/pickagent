package io.github.jamielu.agent.openai;

import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import io.github.jamielu.agent.api.ToolResult;
import io.github.jamielu.agent.openai.OpenAiFunctionCallOutputMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Preserves the OpenAI Responses protocol items needed to continue one or more
 * reasoning-model tool calls.
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
        var callIds = new HashSet<String>();

        for (ResponseOutputItem item : outputSnapshot) {
            if (item.isReasoning()) {
                protocolItems.add(ResponseInputItem.ofReasoning(item.asReasoning()));
            } else if (item.isMessage()) {
                protocolItems.add(ResponseInputItem.ofResponseOutputMessage(item.asMessage()));
            } else if (item.isFunctionCall()) {
                ResponseFunctionToolCall call = item.asFunctionCall();
                if (call.callId().isBlank()) {
                    throw new OpenAiResponseLedgerException(
                            OpenAiResponseLedgerException.Reason.INVALID_CALL_ID,
                            "function_call call_id must not be blank");
                }
                if (!callIds.add(call.callId())) {
                    throw new OpenAiResponseLedgerException(
                            OpenAiResponseLedgerException.Reason.DUPLICATE_CALL_ID,
                            "duplicate function_call call_id: " + call.callId());
                }
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
                    "expected at least one function_call but found 0");
        }

        return new PreparedLedger(protocolItems, calls);
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
        return appendAll(prepared, List.of(Objects.requireNonNull(toolResult, "toolResult")));
    }

    /**
     * Appends a complete result batch after all calls have executed successfully.
     * Results must match calls by count and response order.
     *
     * @param prepared validated protocol history and function calls
     * @param toolResults completed results in response-call order
     * @return immutable continuation input containing history followed by outputs
     */
    public List<ResponseInputItem> appendAll(
            PreparedLedger prepared,
            List<ToolResult> toolResults) {
        Objects.requireNonNull(prepared, "prepared");
        List<ToolResult> resultSnapshot = List.copyOf(
                Objects.requireNonNull(toolResults, "toolResults"));
        if (prepared.functionCalls().size() != resultSnapshot.size()) {
            throw new OpenAiResponseLedgerException(
                    OpenAiResponseLedgerException.Reason.RESULT_COUNT_MISMATCH,
                    "expected " + prepared.functionCalls().size()
                            + " tool results but found " + resultSnapshot.size());
        }

        List<ResponseInputItem> continuation = new ArrayList<>(prepared.protocolItems());
        OpenAiFunctionCallOutputMapper outputMapper = new OpenAiFunctionCallOutputMapper();
        for (int index = 0; index < resultSnapshot.size(); index++) {
            ResponseFunctionToolCall call = prepared.functionCalls().get(index);
            ToolResult result = resultSnapshot.get(index);
            if (!call.callId().equals(result.callId())) {
                throw new OpenAiResponseLedgerException(
                        OpenAiResponseLedgerException.Reason.CALL_ID_MISMATCH,
                        "tool result at index " + index + " has callId '" + result.callId()
                                + "' but expected '" + call.callId() + "'");
            }
            continuation.add(ResponseInputItem.ofFunctionCallOutput(outputMapper.map(result)));
        }
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
        private final List<ResponseFunctionToolCall> functionCalls;

        private PreparedLedger(
                List<ResponseInputItem> protocolItems,
                List<ResponseFunctionToolCall> functionCalls) {
            this.protocolItems = List.copyOf(protocolItems);
            this.functionCalls = List.copyOf(functionCalls);
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
         * Returns all function calls in their original response order.
         *
         * @return immutable original SDK function-call objects
         */
        public List<ResponseFunctionToolCall> functionCalls() {
            return functionCalls;
        }
    }
}


