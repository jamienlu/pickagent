package io.github.jamielu.agent.openai;

import com.openai.models.responses.ResponseOutputItem;
import io.github.jamielu.agent.api.AgentDecision;

import java.util.List;
import java.util.Objects;

/** 将异构 Responses 输出安全解码为供应商中立的单步决策。 */
final class OpenAiResponseDecoder {
    private final OpenAiFunctionCallMapper callMapper = new OpenAiFunctionCallMapper();

    /** 解码完整输出，未知终态条目和缺失文本均采用失败关闭策略。 */
    AgentDecision decode(List<ResponseOutputItem> outputItems) {
        List<ResponseOutputItem> snapshot = List.copyOf(
                Objects.requireNonNull(outputItems, "response output"));
        boolean hasFunctionCall = snapshot.stream().anyMatch(ResponseOutputItem::isFunctionCall);
        if (hasFunctionCall) {
            // 在运行时可能执行工具处理器之前，先校验完整的异构输出。
            new OpenAiResponseLedger().prepare(snapshot);
            return callMapper.map(snapshot);
        }

        for (ResponseOutputItem item : snapshot) {
            if (item.isReasoning()) {
                continue;
            }
            if (!item.isMessage()) {
                throw new OpenAiResponsesModelException(
                        OpenAiResponsesModelException.Reason.UNEXPECTED_FINAL_OUTPUT_ITEM,
                        "terminal response contains unsupported output item: "
                                + OpenAiResponseLedger.itemType(item));
            }
        }
        String answer = snapshot.stream()
                .filter(ResponseOutputItem::isMessage)
                .flatMap(item -> item.asMessage().content().stream())
                .filter(content -> content.outputText().isPresent())
                .map(content -> content.asOutputText().text())
                .reduce("", String::concat);
        if (answer.isBlank()) {
            throw new OpenAiResponsesModelException(
                    OpenAiResponsesModelException.Reason.MISSING_FINAL_TEXT,
                    "terminal response did not contain output text");
        }
        return new AgentDecision.FinalAnswer(answer);
    }
}
