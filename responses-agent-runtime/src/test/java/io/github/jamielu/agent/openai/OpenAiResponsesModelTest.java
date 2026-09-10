package io.github.jamielu.agent.openai;

import com.openai.client.OpenAIClient;
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
import io.github.jamielu.agent.runtime.AgentModelPort;
import io.github.jamielu.agent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
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
    // 场景：首轮请求包含完整模型、指令、工具和安全配置；行为：执行对应代码路径；预期：相关业务断言全部成立。
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

    // 场景：续接请求使用上一响应标识及匹配的函数输出；行为：执行对应代码路径；预期：相关业务断言全部成立。
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

    // 场景：运行时执行 SDK 工具调用后续接到最终回答；行为：执行对应代码路径；预期：相关业务断言全部成立。
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

    // 场景：运行时可跨多个已存储响应连续处理工具调用；行为：执行对应代码路径；预期：相关业务断言全部成立。
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

    // 场景：核心单调用适配器面对多个函数调用时失败关闭；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void multipleFunctionCallsFailClosed() {
        Response response = response("resp_multi", List.of(
                ResponseOutputItem.ofFunctionCall(functionCall("call_1")),
                ResponseOutputItem.ofFunctionCall(functionCall("call_2"))));
        var model = new OpenAiResponsesModel(params -> response, "gpt-test");

        assertThrows(OpenAiFunctionCallMappingException.class,
                () -> model.decide(initialContext("Find orders")));
    }

    // 场景：不支持的工具输出在运行时产生副作用前失败关闭；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void unsupportedToolOutputFailsClosedBeforeRuntimeCanExecute() {
        Response response = response("resp_unknown", List.of(
                ResponseOutputItem.ofFileSearchCall(fileSearchCall()),
                ResponseOutputItem.ofFunctionCall(functionCall("call_1"))));
        var model = new OpenAiResponsesModel(params -> response, "gpt-test");

        assertThrows(OpenAiResponseLedgerException.class,
                () -> model.decide(initialContext("Find ORD-001")));
    }

    // 场景：供应商返回空白响应标识时失败关闭；行为：执行对应代码路径；预期：相关业务断言全部成立。
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

    // 场景：终态响应没有可见文本时失败关闭；行为：执行对应代码路径；预期：相关业务断言全部成立。
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

    // 场景：终态包含未知条目时即使有文本也失败关闭；行为：执行对应代码路径；预期：相关业务断言全部成立。
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

    // 场景：续接输入变化时在传输调用前拒绝；行为：执行对应代码路径；预期：相关业务断言全部成立。
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

    // 场景：配置自定义输出与存储选项；行为：构建首轮请求；预期：上限和 store 值显式写入。
    @Test
    void requestUsesExplicitResponseOptions() {
        RecordingTransport transport = new RecordingTransport(
                finalResponse("resp-final", "done"));
        var model = new OpenAiResponsesModel(
                transport, "gpt-test", Optional.empty(),
                new OpenAiResponseOptions(77, false));

        model.decide(initialContext("question"));

        ResponseCreateParams params = transport.requests().getFirst();
        assertEquals(77, params.maxOutputTokens().orElseThrow());
        assertFalse(params.store().orElseThrow());
    }

    // 场景：通过 SDK 客户端各构造器装配适配器；行为：仅创建实例；预期：不会提前发起网络请求。
    @Test
    void sdkClientConstructorsOnlyConfigureTransport() {
        OpenAIClient client = unusedClient();

        assertTrue(new OpenAiResponsesModel(client, "gpt-test") instanceof AgentModelPort);
        assertTrue(new OpenAiResponsesModel(
                client, "gpt-test", Optional.of("instruction")) instanceof AgentModelPort);
        assertTrue(new OpenAiResponsesModel(
                client, "gpt-test", Optional.empty(),
                new OpenAiResponseOptions(10, true)) instanceof AgentModelPort);
    }

    // 场景：模型适配器构造参数为空或空白；行为：执行边界校验；预期：所有非法依赖都被拒绝。
    @Test
    void constructorRejectsInvalidDependenciesAndText() {
        OpenAiResponsesTransport transport = params -> finalResponse("resp", "done");

        assertThrows(NullPointerException.class,
                () -> new OpenAiResponsesModel((OpenAiResponsesTransport) null, "model"));
        assertThrows(IllegalArgumentException.class,
                () -> new OpenAiResponsesModel(transport, null));
        assertThrows(IllegalArgumentException.class,
                () -> new OpenAiResponsesModel(transport, " "));
        assertThrows(NullPointerException.class,
                () -> new OpenAiResponsesModel(transport, "model", null));
        assertThrows(IllegalArgumentException.class,
                () -> new OpenAiResponsesModel(
                        transport, "model", Optional.of(" ")));
        assertThrows(NullPointerException.class,
                () -> new OpenAiResponsesModel(
                        transport, "model", Optional.empty(), null));
    }

    // 场景：模型调用上下文为空或传输返回空响应；行为：执行一次决策；预期：在状态更新前快速失败。
    @Test
    void decideRejectsNullContextAndNullTransportResponse() {
        var model = new OpenAiResponsesModel(params -> null, "gpt-test");

        assertThrows(NullPointerException.class, () -> model.decide(null));
        assertThrows(NullPointerException.class,
                () -> model.decide(initialContext("question")));
    }

    // 场景：首次调用错误地携带历史；行为：尝试发送续接；预期：因没有待处理函数调用而拒绝且不访问传输。
    @Test
    void continuationBeforeInitialDecisionFails() {
        RecordingTransport transport = new RecordingTransport();
        var model = new OpenAiResponsesModel(transport, "gpt-test");
        AgentDecision.ToolCall call = toolCall("call");

        OpenAiResponsesModelException failure = assertThrows(
                OpenAiResponsesModelException.class,
                () -> model.decide(contextWithExchange("question", call)));

        assertEquals(OpenAiResponsesModelException.Reason.CONTEXT_MISMATCH, failure.reason());
        assertTrue(transport.requests().isEmpty());
    }

    // 场景：最终回答后错误地继续携带工具历史；行为：尝试续接；预期：pending call 为空分支拒绝请求。
    @Test
    void continuationAfterFinalAnswerFails() {
        RecordingTransport transport = new RecordingTransport(
                finalResponse("resp-final", "done"));
        var model = new OpenAiResponsesModel(transport, "gpt-test");
        model.decide(initialContext("question"));

        assertThrows(OpenAiResponsesModelException.class,
                () -> model.decide(contextWithExchange("question", toolCall("call"))));
        assertEquals(1, transport.requests().size());
    }

    // 场景：续接时工具定义被更换；行为：验证运行内上下文；预期：不发送第二次请求。
    @Test
    void changedToolsFailContinuation() {
        RecordingTransport transport = new RecordingTransport(
                toolResponse("resp-tool", "call_001"));
        var model = new OpenAiResponsesModel(transport, "gpt-test");
        AgentContext first = initialContext("question");
        AgentDecision.ToolCall call = assertInstanceOf(
                AgentDecision.ToolCall.class, model.decide(first));

        assertThrows(OpenAiResponsesModelException.class,
                () -> model.decide(new AgentContext(
                        first.input(),
                        List.of(new AgentContext.Exchange(
                                call, new ToolResult(call.callId(), "result"))),
                        List.of())));
        assertEquals(1, transport.requests().size());
    }

    // 场景：续接历史一次增加两个结果；行为：验证历史增量；预期：拒绝非单步推进。
    @Test
    void multipleNewHistoryEntriesFailContinuation() {
        RecordingTransport transport = new RecordingTransport(
                toolResponse("resp-tool", "call_001"));
        var model = new OpenAiResponsesModel(transport, "gpt-test");
        AgentContext first = initialContext("question");
        AgentDecision.ToolCall call = assertInstanceOf(
                AgentDecision.ToolCall.class, model.decide(first));
        AgentDecision.ToolCall extra = toolCall("call-extra");

        assertThrows(OpenAiResponsesModelException.class,
                () -> model.decide(new AgentContext(
                        first.input(),
                        List.of(
                                new AgentContext.Exchange(call,
                                        new ToolResult(call.callId(), "result")),
                                new AgentContext.Exchange(extra,
                                        new ToolResult(extra.callId(), "result"))),
                        first.tools())));
        assertEquals(1, transport.requests().size());
    }

    // 场景：续接历史的调用标识与待处理调用不同；行为：验证关联；预期：拒绝错配并保留第一轮状态。
    @Test
    void mismatchedPendingCallIdFailsContinuation() {
        RecordingTransport transport = new RecordingTransport(
                toolResponse("resp-tool", "call_001"));
        var model = new OpenAiResponsesModel(transport, "gpt-test");
        AgentContext first = initialContext("question");
        model.decide(first);
        AgentDecision.ToolCall wrong = toolCall("call-wrong");

        assertThrows(OpenAiResponsesModelException.class,
                () -> model.decide(contextWithExchange(first.input(), wrong)));
        assertEquals(1, transport.requests().size());
    }

    private static AgentDecision.ToolCall toolCall(String callId) {
        return new AgentDecision.ToolCall(
                callId, "lookup_order", java.util.Map.of("orderId", "ORD-001"));
    }

    private static AgentContext contextWithExchange(
            String input, AgentDecision.ToolCall call) {
        return new AgentContext(input,
                List.of(new AgentContext.Exchange(
                        call, new ToolResult(call.callId(), "result"))),
                List.of(ReplayOrderTool.DEFINITION));
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

    private static OpenAIClient unusedClient() {
        return (OpenAIClient) Proxy.newProxyInstance(
                OpenAIClient.class.getClassLoader(),
                new Class<?>[]{OpenAIClient.class},
                (proxy, method, args) -> {
                    throw new AssertionError(
                            "constructor must not call SDK client: " + method.getName());
                });
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
