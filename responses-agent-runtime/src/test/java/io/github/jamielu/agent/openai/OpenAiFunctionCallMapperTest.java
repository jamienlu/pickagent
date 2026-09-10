package io.github.jamielu.agent.openai;

import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseReasoningItem;
import io.github.jamielu.agent.api.AgentDecision;
import io.github.jamielu.agent.openai.OpenAiFunctionCallMapper;
import io.github.jamielu.agent.openai.OpenAiFunctionCallMappingException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

class OpenAiFunctionCallMapperTest {
    private final OpenAiFunctionCallMapper mapper = new OpenAiFunctionCallMapper();

    // 场景：函数调用映射标识、名称和字符串参数且不执行工具；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void mapsCallIdNameAndStringArgumentsWithoutExecutingATool() {
        AgentDecision.ToolCall mapped = mapper.map(List.of(call(
                "call_order_001", "lookup_order", "{\"orderId\":\"ORD-001\"}")));

        assertEquals("call_order_001", mapped.callId());
        assertEquals("lookup_order", mapped.toolName());
        assertEquals(Map.of("orderId", "ORD-001"), mapped.arguments());
    }

    // 场景：单个函数调用前允许存在推理条目；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void reasoningItemMayPrecedeTheSingleFunctionCall() {
        ResponseReasoningItem reasoning = ResponseReasoningItem.builder()
                .id("rs_001")
                .summary(List.of())
                .build();

        AgentDecision.ToolCall mapped = mapper.map(List.of(
                ResponseOutputItem.ofReasoning(reasoning),
                call("call_after_reasoning", "lookup_order", "{\"orderId\":\"ORD-001\"}")));

        assertEquals("call_after_reasoning", mapped.callId());
    }

    // 场景：畸形参数 JSON 以明确分类失败；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void malformedArgumentsJsonFailsExplicitly() {
        var failure = assertThrows(OpenAiFunctionCallMappingException.class,
                () -> mapper.map(List.of(call("call_bad", "lookup_order", "{not-json"))));

        assertEquals(OpenAiFunctionCallMappingException.Reason.MALFORMED_ARGUMENTS_JSON, failure.reason());
        assertEquals("function_call arguments must be valid JSON", failure.getMessage());
        assertNotNull(failure.getCause());
    }

    // 场景：数组参数根节点被明确拒绝；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void arrayArgumentsRootFailsExplicitly() {
        assertFailure("[\"ORD-001\"]",
                OpenAiFunctionCallMappingException.Reason.ARGUMENTS_NOT_OBJECT,
                "function_call arguments root must be a JSON object");
    }

    // 场景：非字符串参数值被明确拒绝；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void nonStringArgumentValueFailsExplicitly() {
        assertFailure("{\"orderId\":123}",
                OpenAiFunctionCallMappingException.Reason.NON_STRING_ARGUMENT,
                "function_call argument 'orderId' must be a string");
    }

    // 场景：多个函数调用不会被静默选取其中一个；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void multipleFunctionCallsFailBeforeEitherCanBeSilentlySelected() {
        var failure = assertThrows(OpenAiFunctionCallMappingException.class, () -> mapper.map(List.of(
                call("call_1", "lookup_order", "{\"orderId\":\"ORD-001\"}"),
                call("call_2", "lookup_order", "{\"orderId\":\"ORD-002\"}"))));

        assertEquals(OpenAiFunctionCallMappingException.Reason.MULTIPLE_FUNCTION_CALLS, failure.reason());
        assertEquals("expected exactly one function_call but found 2", failure.getMessage());
    }

    // 场景：批量函数调用按响应顺序映射；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void mapsMultipleFunctionCallsInResponseOrder() {
        List<AgentDecision.ToolCall> calls = mapper.mapAll(List.of(
                call("call_1", "lookup_order", "{\"orderId\":\"ORD-001\"}"),
                call("call_2", "lookup_order", "{\"orderId\":\"ORD-002\"}")));

        assertEquals(List.of("call_1", "call_2"),
                calls.stream().map(AgentDecision.ToolCall::callId).toList());
        assertThrowsExactly(UnsupportedOperationException.class,
                () -> calls.add(calls.getFirst()));
    }

    // 场景：批次重复调用标识以明确分类失败；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void duplicateCallIdInBatchFailsExplicitly() {
        var failure = assertThrows(OpenAiFunctionCallMappingException.class,
                () -> mapper.mapAll(List.of(
                        call("call_same", "lookup_order", "{\"orderId\":\"ORD-001\"}"),
                        call("call_same", "lookup_order", "{\"orderId\":\"ORD-002\"}"))));

        assertEquals(OpenAiFunctionCallMappingException.Reason.DUPLICATE_CALL_ID,
                failure.reason());
    }

    // 场景：第二个调用格式错误时拒绝整个批次；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void malformedSecondCallRejectsTheWholeBatch() {
        var failure = assertThrows(OpenAiFunctionCallMappingException.class,
                () -> mapper.mapAll(List.of(
                        call("call_1", "lookup_order", "{\"orderId\":\"ORD-001\"}"),
                        call("call_2", "lookup_order", "{bad-json"))));

        assertEquals(OpenAiFunctionCallMappingException.Reason.MALFORMED_ARGUMENTS_JSON,
                failure.reason());
    }

    // 场景：没有函数调用的输出以明确分类失败；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void outputWithoutAFunctionCallFailsExplicitly() {
        ResponseReasoningItem reasoning = ResponseReasoningItem.builder()
                .id("rs_only")
                .summary(List.of())
                .build();

        var failure = assertThrows(OpenAiFunctionCallMappingException.class,
                () -> mapper.map(List.of(ResponseOutputItem.ofReasoning(reasoning))));

        assertEquals(OpenAiFunctionCallMappingException.Reason.NO_FUNCTION_CALL, failure.reason());
        assertEquals("expected at least one function_call but found 0", failure.getMessage());
    }

    // 场景：函数调用标识为空白；行为：映射 SDK 调用；预期：以稳定字段错误分类拒绝。
    @Test
    void blankCallIdFailsExplicitly() {
        var failure = assertThrows(OpenAiFunctionCallMappingException.class,
                () -> mapper.map(List.of(call(" ", "lookup_order", "{}"))));

        assertEquals(OpenAiFunctionCallMappingException.Reason.INVALID_FUNCTION_CALL_FIELD,
                failure.reason());
    }

    // 场景：函数名称为空白；行为：映射 SDK 调用；预期：以稳定字段错误分类拒绝。
    @Test
    void blankFunctionNameFailsExplicitly() {
        var failure = assertThrows(OpenAiFunctionCallMappingException.class,
                () -> mapper.map(List.of(call("call-1", " ", "{}"))));

        assertEquals(OpenAiFunctionCallMappingException.Reason.INVALID_FUNCTION_CALL_FIELD,
                failure.reason());
    }

    // 场景：函数参数 JSON 为空白；行为：解析参数；预期：走无底层解析异常的格式错误分支。
    @Test
    void blankArgumentsJsonFailsExplicitly() {
        var failure = assertThrows(OpenAiFunctionCallMappingException.class,
                () -> mapper.map(List.of(call("call-1", "lookup_order", " "))));

        assertEquals(OpenAiFunctionCallMappingException.Reason.MALFORMED_ARGUMENTS_JSON,
                failure.reason());
        assertEquals(null, failure.getCause());
    }

    // 场景：映射入口收到空列表引用；行为：执行映射；预期：立即拒绝空输入。
    @Test
    void rejectsNullOutputList() {
        assertThrows(NullPointerException.class, () -> mapper.mapAll(null));
    }

    private void assertFailure(String arguments,
                               OpenAiFunctionCallMappingException.Reason expectedReason,
                               String expectedMessage) {
        var failure = assertThrows(OpenAiFunctionCallMappingException.class,
                () -> mapper.map(List.of(call("call_bad", "lookup_order", arguments))));
        assertEquals(expectedReason, failure.reason());
        assertEquals(expectedMessage, failure.getMessage());
    }

    private static ResponseOutputItem call(String callId, String name, String arguments) {
        return ResponseOutputItem.ofFunctionCall(ResponseFunctionToolCall.builder()
                .id("fc_" + callId)
                .callId(callId)
                .name(name)
                .arguments(arguments)
                .build());
    }
}


