package io.github.jamielu.agent.openai;

import com.openai.models.responses.ResponseFileSearchToolCall;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.ResponseReasoningItem;
import io.github.jamielu.agent.tool.ToolRegistry;
import io.github.jamielu.agent.tool.ToolExecutionException;
import io.github.jamielu.agent.example.support.ReplayOrderTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiResponseReplayTest {
    // 场景：离线回放只执行一次工具并返回最终答案；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void executesTheToolExactlyOnceAndReturnsTheFinalAnswer() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        ToolRegistry registry = registry(executions);

        var result = new OpenAiResponseReplay().run(
                firstOutput().items(),
                ignored -> finalOutput("订单 ORD-001 已发货。"),
                registry);

        assertEquals(1, executions.get());
        assertEquals("订单 ORD-001 已发货。", result.finalAnswer());
    }

    // 场景：第二轮回放保留原始推理与函数调用对象；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void passesOriginalReasoningAndFunctionCallIntoTheSecondReplayRequest() throws Exception {
        FirstFixture first = firstOutput();
        AtomicReference<List<ResponseInputItem>> received = new AtomicReference<>();

        new OpenAiResponseReplay().run(first.items(), input -> {
            received.set(input);
            return finalOutput("done");
        }, registry(new AtomicInteger()));

        List<ResponseInputItem> input = received.get();
        assertEquals(3, input.size());
        assertSame(first.reasoning(), input.get(0).asReasoning());
        assertSame(first.call(), input.get(1).asFunctionCall());
        assertTrue(input.get(2).isFunctionCallOutput());
    }

    // 场景：观察全程保留调用标识并在最终轮停止；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void preservesCallIdThroughObservationAndStopsAfterOneFinalReplayTurn() throws Exception {
        FirstFixture first = firstOutput();
        AtomicInteger secondTurnCalls = new AtomicInteger();

        var result = new OpenAiResponseReplay().run(first.items(), input -> {
            secondTurnCalls.incrementAndGet();
            assertEquals(first.call().callId(), input.getLast().asFunctionCallOutput().callId());
            return finalOutput("final");
        }, registry(new AtomicInteger()));

        assertEquals(first.call().callId(), result.callId());
        assertEquals(1, secondTurnCalls.get());
        assertEquals("final", result.finalAnswer());
    }

    // 场景：首轮协议无效时处理器零执行；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void invalidFirstTurnProtocolCannotExecuteTheHandler() {
        AtomicInteger executions = new AtomicInteger();
        AtomicInteger secondTurns = new AtomicInteger();
        FirstFixture first = firstOutput();

        var failure = assertThrows(OpenAiResponseLedgerException.class,
                () -> new OpenAiResponseReplay().run(List.of(
                                ResponseOutputItem.ofFileSearchCall(fileSearchCall()),
                                ResponseOutputItem.ofFunctionCall(first.call())),
                        ignored -> {
                            secondTurns.incrementAndGet();
                            return finalOutput("must-not-run");
                        }, registry(executions)));

        assertEquals(OpenAiResponseLedgerException.Reason.UNKNOWN_OUTPUT_ITEM, failure.reason());
        assertEquals(0, executions.get(),
                "complete first-turn protocol validation must precede registry.execute");
        assertEquals(0, secondTurns.get());
    }

    // 场景：第二轮出现意外工具条目时失败关闭；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void unexpectedSecondTurnToolItemFailsClosed() {
        AtomicInteger executions = new AtomicInteger();

        var failure = assertThrows(OpenAiResponseLedgerException.class,
                () -> new OpenAiResponseReplay().run(
                        firstOutput().items(),
                        ignored -> List.of(
                                ResponseOutputItem.ofFileSearchCall(fileSearchCall()),
                                finalOutput("must-not-be-accepted").getFirst()),
                        registry(executions)));

        assertEquals(OpenAiResponseLedgerException.Reason.UNEXPECTED_FINAL_OUTPUT_ITEM,
                failure.reason());
        assertTrue(failure.getMessage().contains("file_search_call"));
        assertEquals(1, executions.get(), "the valid first turn executes exactly once");
    }

    // 场景：多个调用按序串行执行并在原条目后追加输出；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void executesMultipleCallsSeriallyAndAppendsOutputsAfterOriginalItems() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        AtomicReference<List<ResponseInputItem>> received = new AtomicReference<>();
        ResponseReasoningItem reasoning = firstOutput().reasoning();
        ResponseFunctionToolCall first = functionCall(
                "call_1", "lookup_order", "{\"orderId\":\"ORD-001\"}");
        ResponseFunctionToolCall second = functionCall(
                "call_2", "lookup_order", "{\"orderId\":\"ORD-002\"}");

        var result = new OpenAiResponseReplay().run(List.of(
                        ResponseOutputItem.ofReasoning(reasoning),
                        ResponseOutputItem.ofFunctionCall(first),
                        ResponseOutputItem.ofFunctionCall(second)),
                input -> {
                    received.set(input);
                    return finalOutput("batch complete");
                }, registry(executions));

        List<ResponseInputItem> input = received.get();
        assertEquals(5, input.size());
        assertSame(first, input.get(1).asFunctionCall());
        assertSame(second, input.get(2).asFunctionCall());
        assertEquals("call_1", input.get(3).asFunctionCallOutput().callId());
        assertEquals("call_2", input.get(4).asFunctionCallOutput().callId());
        assertEquals(List.of("call_1", "call_2"), result.callIds());
        assertThrows(IllegalStateException.class, result::callId);
        assertEquals(2, executions.get());
    }

    // 场景：第二轮只有推理条目而没有可见文本；行为：提取最终答案；预期：明确拒绝缺失答案。
    @Test
    void secondTurnWithoutVisibleTextFailsExplicitly() {
        assertThrows(IllegalStateException.class,
                () -> new OpenAiResponseReplay().run(
                        firstOutput().items(),
                        ignored -> List.of(ResponseOutputItem.ofReasoning(
                                ResponseReasoningItem.builder().id("rs-final")
                                        .summary(List.of()).build())),
                        registry(new AtomicInteger())));
    }

    // 场景：回放证据的最终答案为空引用；行为：构造不可变结果；预期：拒绝无效终态。
    @Test
    void replayResultRejectsNullFinalAnswer() {
        assertThrows(NullPointerException.class,
                () -> new OpenAiResponseReplay.ReplayResult(
                        List.of(), List.of(), List.of(), null));
    }

    // 场景：批次第二个工具未知时首个处理器也不执行；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void unknownSecondToolFailsBeforeTheFirstHandlerRuns() {
        AtomicInteger executions = new AtomicInteger();
        AtomicInteger secondTurns = new AtomicInteger();

        assertThrows(ToolRegistry.RejectedCall.class,
                () -> new OpenAiResponseReplay().run(List.of(
                                ResponseOutputItem.ofFunctionCall(functionCall(
                                        "call_1", "lookup_order", "{\"orderId\":\"ORD-001\"}")),
                                ResponseOutputItem.ofFunctionCall(functionCall(
                                        "call_2", "missing_tool", "{\"orderId\":\"ORD-002\"}"))),
                        ignored -> {
                            secondTurns.incrementAndGet();
                            return finalOutput("must-not-run");
                        }, registry(executions)));

        assertEquals(0, executions.get());
        assertEquals(0, secondTurns.get());
    }

    // 场景：批次第二个调用格式错误时首个处理器也不执行；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void malformedSecondCallFailsBeforeTheFirstHandlerRuns() {
        AtomicInteger executions = new AtomicInteger();

        assertThrows(OpenAiFunctionCallMappingException.class,
                () -> new OpenAiResponseReplay().run(List.of(
                                ResponseOutputItem.ofFunctionCall(functionCall(
                                        "call_1", "lookup_order", "{\"orderId\":\"ORD-001\"}")),
                                ResponseOutputItem.ofFunctionCall(functionCall(
                                        "call_2", "lookup_order", "{bad-json"))),
                        ignored -> finalOutput("must-not-run"), registry(executions)));

        assertEquals(0, executions.get());
    }

    // 场景：批次执行失败后停止剩余调用并跳过第二轮；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void executionFailureStopsTheBatchAndSkipsTheSecondTurn() {
        AtomicInteger executions = new AtomicInteger();
        AtomicInteger secondTurns = new AtomicInteger();
        ToolRegistry registry = new ToolRegistry(List.of(new ToolRegistry.Registration(
                ReplayOrderTool.DEFINITION,
                arguments -> {
                    executions.incrementAndGet();
                    if (arguments.get("orderId").equals("ORD-FAIL")) {
                        throw new ToolExecutionException("fixture failure");
                    }
                    return "ok";
                })));

        assertThrows(ToolExecutionException.class,
                () -> new OpenAiResponseReplay().run(List.of(
                                ResponseOutputItem.ofFunctionCall(functionCall(
                                        "call_1", "lookup_order", "{\"orderId\":\"ORD-001\"}")),
                                ResponseOutputItem.ofFunctionCall(functionCall(
                                        "call_2", "lookup_order", "{\"orderId\":\"ORD-FAIL\"}")),
                                ResponseOutputItem.ofFunctionCall(functionCall(
                                        "call_3", "lookup_order", "{\"orderId\":\"ORD-003\"}"))),
                        ignored -> {
                            secondTurns.incrementAndGet();
                            return finalOutput("must-not-run");
                        }, registry));

        assertEquals(2, executions.get(), "the third call must not execute");
        assertEquals(0, secondTurns.get());
    }

    private static ToolRegistry registry(AtomicInteger executions) {
        ReplayOrderTool tool = new ReplayOrderTool();
        return new ToolRegistry(List.of(new ToolRegistry.Registration(
                ReplayOrderTool.DEFINITION,
                arguments -> {
                    executions.incrementAndGet();
                    return tool.execute(arguments);
                })));
    }

    private static FirstFixture firstOutput() {
        ResponseReasoningItem reasoning = ResponseReasoningItem.builder()
                .id("rs_replay")
                .summary(List.of())
                .encryptedContent("opaque-replay-reasoning")
                .build();
        ResponseFunctionToolCall call = functionCall(
                "call_replay", "lookup_order", "{\"orderId\":\"ORD-001\"}");
        return new FirstFixture(reasoning, call, List.of(
                ResponseOutputItem.ofReasoning(reasoning),
                ResponseOutputItem.ofFunctionCall(call)));
    }

    private static ResponseFunctionToolCall functionCall(
            String callId,
            String name,
            String arguments) {
        return ResponseFunctionToolCall.builder()
                .id("fc_" + callId)
                .callId(callId)
                .name(name)
                .arguments(arguments)
                .status(ResponseFunctionToolCall.Status.COMPLETED)
                .build();
    }

    private static List<ResponseOutputItem> finalOutput(String text) {
        ResponseOutputMessage message = ResponseOutputMessage.builder()
                .id("msg_final")
                .status(ResponseOutputMessage.Status.COMPLETED)
                .addContent(ResponseOutputText.builder()
                        .annotations(List.of())
                        .text(text)
                        .build())
                .build();
        return List.of(ResponseOutputItem.ofMessage(message));
    }

    private static ResponseFileSearchToolCall fileSearchCall() {
        return ResponseFileSearchToolCall.builder()
                .id("fs_unexpected")
                .queries(List.of("order"))
                .status(ResponseFileSearchToolCall.Status.COMPLETED)
                .build();
    }

    private record FirstFixture(
            ResponseReasoningItem reasoning,
            ResponseFunctionToolCall call,
            List<ResponseOutputItem> items) {
    }
}


