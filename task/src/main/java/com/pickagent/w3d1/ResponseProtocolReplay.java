package com.pickagent.w3d1;

import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import com.pickagent.w2.core.AgentDecision;
import com.pickagent.w2.core.ToolExecutionException;
import com.pickagent.w2.core.ToolRegistry;
import com.pickagent.w2.core.ToolResult;
import com.pickagent.w2.openai.OpenAiFunctionCallMapper;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Runs one deterministic function-call continuation entirely from SDK fixtures. */
public final class ResponseProtocolReplay {
    private final OpenAiFunctionCallMapper callMapper = new OpenAiFunctionCallMapper();
    private final OpenAiResponseLedger ledger = new OpenAiResponseLedger();

    /** Creates an offline replay orchestrator. */
    public ResponseProtocolReplay() {
    }

    /**
     * Executes the single tool call from the first fixture, passes the complete
     * ledger to the second replay turn, and requires a final textual answer.
     *
     * @param firstOutput first response fixture
     * @param secondTurn deterministic second request/response fixture
     * @param registry trusted tool-validation and execution boundary
     * @return immutable evidence from the completed two-turn replay
     * @throws ToolExecutionException when the registered tool reports a known failure
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
        AgentDecision.ToolCall call = callMapper.map(firstSnapshot);
        ToolResult result = registry.execute(call);
        List<ResponseInputItem> continuation = ledger.append(prepared, result);
        List<ResponseOutputItem> finalOutput = List.copyOf(Objects.requireNonNull(
                secondTurn.apply(continuation), "secondTurn returned null"));

        for (ResponseOutputItem item : finalOutput) {
            if (!item.isReasoning() && !item.isMessage()) {
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
        return new ReplayResult(firstSnapshot, continuation, result.callId(), finalAnswer);
    }

    /**
     * Immutable evidence for the protocol history handed to the second turn.
     *
     * @param firstOutput original first-turn output snapshot
     * @param secondInput complete continuation ledger
     * @param callId correlated function-call identifier
     * @param finalAnswer final assistant text
     */
    public record ReplayResult(
            List<ResponseOutputItem> firstOutput,
            List<ResponseInputItem> secondInput,
            String callId,
            String finalAnswer) {
        /** Defensively snapshots all collection components. */
        public ReplayResult {
            firstOutput = List.copyOf(firstOutput);
            secondInput = List.copyOf(secondInput);
            Objects.requireNonNull(callId, "callId");
            Objects.requireNonNull(finalAnswer, "finalAnswer");
        }
    }
}
