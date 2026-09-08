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

/** Runs a deterministic Responses function-call continuation from SDK fixtures. */
public final class OpenAiResponseReplay {
    private final OpenAiFunctionCallMapper callMapper = new OpenAiFunctionCallMapper();
    private final OpenAiResponseLedger ledger = new OpenAiResponseLedger();

    /** Creates an offline replay orchestrator. */
    public OpenAiResponseReplay() {
    }

    /**
     * Preflights the complete call batch, executes calls serially in response
     * order, passes the complete ledger to the next turn, and requires a final
     * textual answer.
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
        return new ReplayResult(
                firstSnapshot,
                continuation,
                results.stream().map(ToolResult::callId).toList(),
                finalAnswer);
    }

    /**
     * Immutable evidence for the protocol history handed to the second turn.
     *
     * @param firstOutput original first-turn output snapshot
     * @param secondInput complete continuation ledger
     * @param callIds correlated function-call identifiers in response order
     * @param finalAnswer final assistant text
     */
    public record ReplayResult(
            List<ResponseOutputItem> firstOutput,
            List<ResponseInputItem> secondInput,
            List<String> callIds,
            String finalAnswer) {
        /** Defensively snapshots all collection components. */
        public ReplayResult {
            firstOutput = List.copyOf(firstOutput);
            secondInput = List.copyOf(secondInput);
            callIds = List.copyOf(callIds);
            Objects.requireNonNull(finalAnswer, "finalAnswer");
        }

        /**
         * Returns the only call id for single-call compatibility.
         *
         * @return the single call id
         */
        public String callId() {
            if (callIds.size() != 1) {
                throw new IllegalStateException("replay contains " + callIds.size() + " call ids");
            }
            return callIds.getFirst();
        }
    }
}


