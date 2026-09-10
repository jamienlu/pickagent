package io.github.jamielu.agent.openai;

import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import io.github.jamielu.agent.api.AgentDecision;
import io.github.jamielu.agent.tool.ToolExecutionException;
import io.github.jamielu.agent.tool.ToolRegistry;
import io.github.jamielu.agent.api.ToolResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** 使用 SDK 固定数据运行确定性的 Responses 函数调用续接。 */
public final class OpenAiResponseReplay {
    private final OpenAiFunctionCallMapper callMapper = new OpenAiFunctionCallMapper();
    private final OpenAiResponseLedger ledger = new OpenAiResponseLedger();

    /** 创建离线回放编排器。 */
    public OpenAiResponseReplay() {
    }

    /**
     * 完整预检调用批次、按响应顺序串行执行并要求最终文本答案。
     *
     * @param firstOutput 首轮响应的不可变输出快照来源
     * @param secondTurn 接收续接输入并返回第二轮输出的函数
     * @param registry 工具白名单与执行注册表
     * @return 包含两轮协议证据与最终回答的回放结果
     * @throws ToolExecutionException 任一已批准工具执行失败
     */
    public ReplayResult run(
            List<ResponseOutputItem> firstOutput,
            Function<List<ResponseInputItem>, List<ResponseOutputItem>> secondTurn,
            ToolRegistry registry) throws ToolExecutionException {
        Objects.requireNonNull(firstOutput, "firstOutput");
        Objects.requireNonNull(secondTurn, "secondTurn");
        Objects.requireNonNull(registry, "registry");

        List<ResponseOutputItem> firstSnapshot = List.copyOf(firstOutput);
        OpenAiResponseLedger.PreparedLedger prepared = ledger.prepare(firstSnapshot);
        List<AgentDecision.ToolCall> calls = callMapper.mapAll(firstSnapshot);
        List<ToolRegistry.PreparedCall> preparedCalls = registry.prepareAll(calls);
        List<ToolResult> results = new ArrayList<>(preparedCalls.size());
        for (ToolRegistry.PreparedCall preparedCall : preparedCalls) {
            results.add(registry.execute(preparedCall));
        }
        List<ResponseInputItem> continuation = ledger.appendAll(prepared, results);
        List<ResponseOutputItem> finalOutput = List.copyOf(Objects.requireNonNull(
                secondTurn.apply(continuation), "secondTurn returned null"));

        for (ResponseOutputItem item : finalOutput) {
            if (item.isReasoning()) {
                continue;
            }
            if (!item.isMessage()) {
                throw new OpenAiResponseLedgerException(
                        OpenAiResponseLedgerException.Reason.UNEXPECTED_FINAL_OUTPUT_ITEM,
                        "second replay turn contains non-final output item: "
                                + OpenAiResponseLedger.itemType(item));
            }
        }
        String finalAnswer = finalOutput.stream()
                .filter(ResponseOutputItem::isMessage)
                .flatMap(item -> item.asMessage().content().stream())
                .filter(content -> content.outputText().isPresent())
                .map(content -> content.asOutputText().text())
                .reduce("", String::concat);
        if (finalAnswer.isBlank()) {
            throw new IllegalStateException("second replay turn did not contain final output text");
        }
        return new ReplayResult(
                firstSnapshot,
                continuation,
                results.stream().map(ToolResult::callId).toList(),
                finalAnswer);
    }

    /**
     * 传递给第二轮的不可变协议历史证据。
     *
     * @param firstOutput 首轮原始输出快照
     * @param secondInput 追加工具结果后的第二轮输入
     * @param callIds 按响应顺序保存的调用标识
     * @param finalAnswer 第二轮最终文本
     */
    public record ReplayResult(
            List<ResponseOutputItem> firstOutput,
            List<ResponseInputItem> secondInput,
            List<String> callIds,
            String finalAnswer) {
        /** 防御性复制所有集合组件。 */
        public ReplayResult {
            firstOutput = List.copyOf(firstOutput);
            secondInput = List.copyOf(secondInput);
            callIds = List.copyOf(callIds);
            Objects.requireNonNull(finalAnswer, "finalAnswer");
        }

        /**
         * 为兼容单调用场景返回唯一调用标识。
         *
         * @return 唯一调用标识
         */
        public String callId() {
            if (callIds.size() != 1) {
                throw new IllegalStateException("replay contains " + callIds.size() + " call ids");
            }
            return callIds.getFirst();
        }
    }
}


