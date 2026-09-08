package io.github.jamielu.agent.example;

import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.ResponseReasoningItem;
import io.github.jamielu.agent.openai.OpenAiResponseReplay;
import io.github.jamielu.agent.tool.ToolExecutionException;
import io.github.jamielu.agent.tool.ToolRegistry;
import io.github.jamielu.agent.example.support.ReplayOrderTool;

import java.io.PrintStream;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/** Observable Java 21 demo of an offline reasoning/tool protocol continuation. */
public final class ResponsesAgentRuntimeDemo {
    private ResponsesAgentRuntimeDemo() {
    }

    /**
     * Runs the deterministic, network-free demonstration.
     *
     * @param args ignored command-line arguments
     */
    public static void main(String[] args) {
        run(System.out);
    }

    static Evidence run(PrintStream out) {
        Objects.requireNonNull(out, "out");
        ResponseReasoningItem reasoning = ResponseReasoningItem.builder()
                .id("rs_ledger_001")
                .summary(List.of())
                .encryptedContent("opaque-reasoning-fixture")
                .build();
        ResponseFunctionToolCall firstCall = ResponseFunctionToolCall.builder()
                .id("fc_order_001")
                .callId("call_order_001")
                .name("lookup_order")
                .arguments("{\"orderId\":\"ORD-001\"}")
                .status(ResponseFunctionToolCall.Status.COMPLETED)
                .build();
        ResponseFunctionToolCall secondCall = ResponseFunctionToolCall.builder()
                .id("fc_order_404")
                .callId("call_order_404")
                .name("lookup_order")
                .arguments("{\"orderId\":\"ORD-404\"}")
                .status(ResponseFunctionToolCall.Status.COMPLETED)
                .build();
        List<ResponseOutputItem> firstOutput = List.of(
                ResponseOutputItem.ofReasoning(reasoning),
                ResponseOutputItem.ofFunctionCall(firstCall),
                ResponseOutputItem.ofFunctionCall(secondCall));

        AtomicInteger executions = new AtomicInteger();
        ReplayOrderTool replayTool = new ReplayOrderTool();
        ToolRegistry registry = new ToolRegistry(List.of(new ToolRegistry.Registration(
                ReplayOrderTool.DEFINITION,
                arguments -> {
                    executions.incrementAndGet();
                    return replayTool.execute(arguments);
                })));

        ResponseOutputMessage finalMessage = ResponseOutputMessage.builder()
                .id("msg_ledger_final")
                .status(ResponseOutputMessage.Status.COMPLETED)
                .addContent(ResponseOutputText.builder()
                        .annotations(List.of())
                        .text("ORD-001 is SHIPPED; ORD-404 was not found.")
                        .build())
                .build();

        OpenAiResponseReplay.ReplayResult result;
        try {
            result = new OpenAiResponseReplay().run(
                    firstOutput,
                    ignored -> List.of(ResponseOutputItem.ofMessage(finalMessage)),
                    registry);
        } catch (ToolExecutionException unexpectedFixtureFailure) {
            throw new IllegalStateException("offline fixture tool failed", unexpectedFixtureFailure);
        }

        boolean callIdMatches = result.callIds().equals(List.of(
                firstCall.callId(), secondCall.callId()))
                && firstCall.callId().equals(result.secondInput().get(3)
                        .asFunctionCallOutput().callId())
                && secondCall.callId().equals(result.secondInput().get(4)
                        .asFunctionCallOutput().callId());
        boolean originalItemsPreserved = result.secondInput().get(0).asReasoning() == reasoning
                && result.secondInput().get(1).asFunctionCall() == firstCall
                && result.secondInput().get(2).asFunctionCall() == secondCall;
        if (!callIdMatches || !originalItemsPreserved || executions.get() != 2) {
            throw new IllegalStateException("protocol-ledger invariant failed");
        }

        out.println("items.before=" + outputTypes(result.firstOutput()));
        out.println("items.after=" + inputTypes(result.secondInput()));
        out.println("call_ids.match=" + callIdMatches);
        out.println("tool.executions=" + executions.get());
        out.println("final.answer=" + result.finalAnswer());
        out.println("ledger.proof=PASS");
        return new Evidence(result, executions.get(), originalItemsPreserved);
    }

    private static String outputTypes(List<ResponseOutputItem> items) {
        return items.stream().map(item -> {
            if (item.isReasoning()) {
                return "reasoning";
            }
            if (item.isMessage()) {
                return "assistant/message";
            }
            if (item.isFunctionCall()) {
                return "function_call";
            }
            return "unknown";
        }).reduce((left, right) -> left + "," + right).orElse("");
    }

    private static String inputTypes(List<ResponseInputItem> items) {
        return items.stream().map(item -> {
            if (item.isReasoning()) {
                return "reasoning";
            }
            if (item.isResponseOutputMessage()) {
                return "assistant/message";
            }
            if (item.isFunctionCall()) {
                return "function_call";
            }
            if (item.isFunctionCallOutput()) {
                return "function_call_output";
            }
            return "unknown";
        }).reduce((left, right) -> left + "," + right).orElse("");
    }

    record Evidence(
            OpenAiResponseReplay.ReplayResult result,
            int toolExecutions,
            boolean originalItemsPreserved) {
    }
}


