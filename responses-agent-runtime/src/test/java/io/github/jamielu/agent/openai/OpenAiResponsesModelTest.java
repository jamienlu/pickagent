package io.github.jamielu.agent.openai;

import com.openai.core.JsonValue;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFileSearchToolCall;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.ResponseReasoningItem;
import com.openai.models.responses.ToolChoiceOptions;
import io.github.jamielu.agent.api.AgentContext;
import io.github.jamielu.agent.api.AgentDecision;
import io.github.jamielu.agent.api.ToolResult;
import io.github.jamielu.agent.example.support.ReplayOrderTool;
import io.github.jamielu.agent.runtime.AgentRuntime;
import io.github.jamielu.agent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiResponsesModelTest {
    @Test
    void initialRequestContainsCompleteStatelessConfiguration() {
        RecordingTransport transport = new RecordingTransport(toolResponse("resp_tool", "call_001"));
        var model = new OpenAiResponsesModel(
                transport, "gpt-test", Optional.of("Use tools when required."));

        AgentDecision.ToolCall decision = assertInstanceOf(AgentDecision.ToolCall.class,
                model.decide(initialContext("Find ORD-001")));

        assertEquals("call_001", decision.callId());
        ResponseCreateParams params = transport.requests().getFirst();
        assertEquals("gpt-test", params.model().orElseThrow().asString());
        assertEquals("Find ORD-001", params.input().orElseThrow().asText());
        assertEquals("Use tools when required.", params.instructions().orElseThrow());
        assertTrue(params.store().orElseThrow());
        assertFalse(params.parallelToolCalls().orElseThrow());
        assertTrue(params.previousResponseId().isEmpty());
        assertEquals(1, params.tools().orElseThrow().size());
        assertEquals("lookup_order",
                params.tools().orElseThrow().getFirst().asFunction().name());
    }

    @Test
    void continuationUsesPreviousResponseIdAndMatchingFunctionOutput() {
        RecordingTransport transport = new RecordingTransport(
                toolResponse("resp_tool", "call_001"),
                finalResponse("resp_final", "Order ORD-001 is shipped."));
        var model = new OpenAiResponsesModel(
                transport, "gpt-test", Optional.of("Use tools when required."));
        AgentContext first = initialContext("Find ORD-001");
        AgentDecision.ToolCall call = assertInstanceOf(
                AgentDecision.ToolCall.class, model.decide(first));

        AgentDecision.FinalAnswer answer = assertInstanceOf(AgentDecision.FinalAnswer.class,
                model.decide(new AgentContext(first.input(),
                        List.of(new AgentContext.Exchange(
                                call, new ToolResult(call.callId(), "SHIPPED"))),
                        first.tools())));

        assertEquals("Order ORD-001 is shipped.", answer.text());
        ResponseCreateParams continuation = transport.requests().get(1);
        assertEquals("resp_tool", continuation.previousResponseId().orElseThrow());
        assertEquals("gpt-test", continuation.model().orElseThrow().asString());
        assertEquals("Use tools when required.", continuation.instructions().orElseThrow());
        assertTrue(continuation.store().orElseThrow());
        assertFalse(continuation.parallelToolCalls().orElseThrow());
        assertEquals(1, continuation.tools().orElseThrow().size());
        var items = continuation.input().orElseThrow().asResponse();
        assertEquals(1, items.size());
        assertTrue(items.getFirst().isFunctionCallOutput());
        assertEquals("call_001", items.getFirst().asFunctionCallOutput().callId());
        assertEquals("SHIPPED",
                items.getFirst().asFunctionCallOutput().output().asString());
    }

    @Test
    void runtimeExecutesSdkToolCallAndContinuesToFinalAnswer() {
        RecordingTransport transport = new RecordingTransport(
                toolResponse("resp_tool", "call_001"),
                finalResponse("resp_final", "The order has shipped."));
        AtomicInteger executions = new AtomicInteger();
        ReplayOrderTool tool = new ReplayOrderTool();
        ToolRegistry registry = new ToolRegistry(List.of(new ToolRegistry.Registration(
                ReplayOrderTool.DEFINITION,
                arguments -> {
                    executions.incrementAndGet();
                    return tool.execute(arguments);
                })));
        AgentRuntime runtime = AgentRuntime.withModelFactory(
                () -> new OpenAiResponsesModel(transport, "gpt-test"), registry, 3);

        AgentRuntime.Completed completed = assertInstanceOf(
                AgentRuntime.Completed.class, runtime.run("Find ORD-001"));

        assertEquals("The order has shipped.", completed.answer().text());
        assertEquals(1, executions.get());
        assertEquals(2, transport.requests().size());
        assertEquals("Order ORD-001: SHIPPED",
                transport.requests().get(1).input().orElseThrow().asResponse()
                        .getFirst().asFunctionCallOutput().output().asString());
    }

    @Test
    void runtimeCanContinueAcrossMultipleStoredResponses() {
        RecordingTransport transport = new RecordingTransport(
                toolResponse("resp_1", "call_1"),
                toolResponse("resp_2", "call_2"),
                finalResponse("resp_3", "Both lookups completed."));
        AtomicInteger executions = new AtomicInteger();
        ToolRegistry registry = new ToolRegistry(List.of(new ToolRegistry.Registration(
                ReplayOrderTool.DEFINITION,
                arguments -> "execution-" + executions.incrementAndGet())));
        AgentRuntime runtime = AgentRuntime.withModelFactory(
                () -> new OpenAiResponsesModel(transport, "gpt-test"), registry, 4);

        AgentRuntime.Completed completed = assertInstanceOf(
                AgentRuntime.Completed.class, runtime.run("Run two lookups"));

        assertEquals("Both lookups completed.", completed.answer().text());
        assertEquals(2, executions.get());
        assertEquals(3, transport.requests().size());
        assertContinuation(transport.requests().get(1), "resp_1", "call_1", "execution-1");
        assertContinuation(transport.requests().get(2), "resp_2", "call_2", "execution-2");
    }

    @Test
    void multipleFunctionCallsFailClosed() {
        Response response = response("resp_multi", List.of(
                ResponseOutputItem.ofFunctionCall(functionCall("call_1")),
                ResponseOutputItem.ofFunctionCall(functionCall("call_2"))));
        var model = new OpenAiResponsesModel(params -> response, "gpt-test");

        assertThrows(OpenAiFunctionCallMappingException.class,
                () -> model.decide(initialContext("Find orders")));
    }

    @Test
    void unsupportedToolOutputFailsClosedBeforeRuntimeCanExecute() {
        Response response = response("resp_unknown", List.of(
                ResponseOutputItem.ofFileSearchCall(fileSearchCall()),
                ResponseOutputItem.ofFunctionCall(functionCall("call_1"))));
        var model = new OpenAiResponsesModel(params -> response, "gpt-test");

        assertThrows(OpenAiResponseLedgerException.class,
                () -> model.decide(initialContext("Find ORD-001")));
    }

    @Test
    void blankResponseIdFailsClosed() {
        var model = new OpenAiResponsesModel(
                params -> response(" ", List.of(ResponseOutputItem.ofMessage(message("ignored")))),
                "gpt-test");

        OpenAiResponsesModelException failure = assertThrows(
                OpenAiResponsesModelException.class,
                () -> model.decide(initialContext("Question")));

        assertEquals(OpenAiResponsesModelException.Reason.INVALID_RESPONSE_ID, failure.reason());
    }

    @Test
    void terminalResponseWithoutTextFailsClosed() {
        var model = new OpenAiResponsesModel(
                params -> response("resp_empty", List.of(
                        ResponseOutputItem.ofReasoning(reasoning()))),
                "gpt-test");

        OpenAiResponsesModelException failure = assertThrows(
                OpenAiResponsesModelException.class,
                () -> model.decide(initialContext("Question")));

        assertEquals(OpenAiResponsesModelException.Reason.MISSING_FINAL_TEXT, failure.reason());
    }

    @Test
    void unsupportedTerminalOutputItemFailsClosedEvenWhenTextExists() {
        var model = new OpenAiResponsesModel(
                params -> response("resp_unknown", List.of(
                        ResponseOutputItem.ofFileSearchCall(fileSearchCall()),
                        ResponseOutputItem.ofMessage(message("must not be accepted")))),
                "gpt-test");

        OpenAiResponsesModelException failure = assertThrows(
                OpenAiResponsesModelException.class,
                () -> model.decide(initialContext("Question")));

        assertEquals(OpenAiResponsesModelException.Reason.UNEXPECTED_FINAL_OUTPUT_ITEM,
                failure.reason());
    }

    @Test
    void changedContinuationContextFailsBeforeTransportCall() {
        RecordingTransport transport = new RecordingTransport(toolResponse("resp_tool", "call_001"));
        var model = new OpenAiResponsesModel(transport, "gpt-test");
        AgentContext first = initialContext("Find ORD-001");
        AgentDecision.ToolCall call = assertInstanceOf(
                AgentDecision.ToolCall.class, model.decide(first));
        AgentContext changed = new AgentContext("Find ORD-002",
                List.of(new AgentContext.Exchange(
                        call, new ToolResult(call.callId(), "SHIPPED"))),
                first.tools());

        OpenAiResponsesModelException failure = assertThrows(
                OpenAiResponsesModelException.class, () -> model.decide(changed));

        assertEquals(OpenAiResponsesModelException.Reason.CONTEXT_MISMATCH, failure.reason());
        assertEquals(1, transport.requests().size());
    }

    private static AgentContext initialContext(String input) {
        return new AgentContext(input, List.of(), List.of(ReplayOrderTool.DEFINITION));
    }

    private static void assertContinuation(
            ResponseCreateParams params,
            String previousResponseId,
            String callId,
            String output) {
        assertEquals(previousResponseId, params.previousResponseId().orElseThrow());
        var item = params.input().orElseThrow().asResponse().getFirst();
        assertEquals(callId, item.asFunctionCallOutput().callId());
        assertEquals(output, item.asFunctionCallOutput().output().asString());
    }

    private static Response toolResponse(String responseId, String callId) {
        return response(responseId,
                List.of(ResponseOutputItem.ofFunctionCall(functionCall(callId))));
    }

    private static Response finalResponse(String responseId, String text) {
        return response(responseId, List.of(ResponseOutputItem.ofMessage(message(text))));
    }

    private static Response response(String id, List<ResponseOutputItem> output) {
        return Response.builder()
                .id(id)
                .createdAt(1.0)
                .error(Optional.empty())
                .incompleteDetails(Optional.empty())
                .instructions(Optional.empty())
                .metadata(Optional.empty())
                .model("gpt-test")
                .object_(JsonValue.from("response"))
                .output(output)
                .parallelToolCalls(false)
                .temperature(Optional.empty())
                .toolChoice(ToolChoiceOptions.AUTO)
                .tools(List.of())
                .topP(Optional.empty())
                .build();
    }

    private static ResponseFunctionToolCall functionCall(String callId) {
        return ResponseFunctionToolCall.builder()
                .id("fc_" + callId)
                .callId(callId)
                .name("lookup_order")
                .arguments("{\"orderId\":\"ORD-001\"}")
                .status(ResponseFunctionToolCall.Status.COMPLETED)
                .build();
    }

    private static ResponseOutputMessage message(String text) {
        return ResponseOutputMessage.builder()
                .id("msg_final")
                .status(ResponseOutputMessage.Status.COMPLETED)
                .addContent(ResponseOutputText.builder()
                        .annotations(List.of())
                        .text(text)
                        .build())
                .build();
    }

    private static ResponseReasoningItem reasoning() {
        return ResponseReasoningItem.builder()
                .id("rs_001")
                .summary(List.of())
                .encryptedContent("opaque-reasoning")
                .build();
    }

    private static ResponseFileSearchToolCall fileSearchCall() {
        return ResponseFileSearchToolCall.builder()
                .id("fs_unexpected")
                .queries(List.of("order"))
                .status(ResponseFileSearchToolCall.Status.COMPLETED)
                .build();
    }

    private static final class RecordingTransport implements OpenAiResponsesTransport {
        private final Queue<Response> responses;
        private final List<ResponseCreateParams> requests = new ArrayList<>();

        private RecordingTransport(Response... responses) {
            this.responses = new ArrayDeque<>(List.of(responses));
        }

        @Override
        public Response create(ResponseCreateParams params) {
            requests.add(params);
            return responses.remove();
        }

        private List<ResponseCreateParams> requests() {
            return List.copyOf(requests);
        }
    }
}
