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

/** 保留一个或多个推理模型工具调用续接所需的 Responses 协议条目。 */
public final class OpenAiResponseLedger {
    /** 创建无状态协议账本。 */
    public OpenAiResponseLedger() {
    }

    /**
     * 在不执行工具或其他副作用的前提下校验并转换响应输出历史。
     *
     * @param outputItems 首轮 Responses 异构输出条目
     * @return 完整校验且保持顺序的协议账本
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
     * 向已校验协议历史追加一个执行结果。
     *
     * @param prepared 已完整校验的协议账本
     * @param toolResult 唯一工具执行结果
     * @return 可发送到续接请求的不可变输入条目
     */
    public List<ResponseInputItem> append(PreparedLedger prepared, ToolResult toolResult) {
        return appendAll(prepared, List.of(Objects.requireNonNull(toolResult, "toolResult")));
    }

    /**
     * 所有调用成功后按响应顺序追加完整结果批次。
     *
     * @param prepared 已完整校验的协议账本
     * @param toolResults 与函数调用顺序和数量精确匹配的结果
     * @return 可发送到续接请求的不可变输入条目
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

    /** 不可变且已经完整验证的首轮协议历史。 */
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
         * 返回保持原始顺序的不可变输入条目。
         *
         * @return 首轮协议输入条目
         */
        public List<ResponseInputItem> protocolItems() {
            return protocolItems;
        }

        /**
         * 返回保持原始响应顺序的全部函数调用。
         *
         * @return 首轮全部函数调用
         */
        public List<ResponseFunctionToolCall> functionCalls() {
            return functionCalls;
        }
    }
}


