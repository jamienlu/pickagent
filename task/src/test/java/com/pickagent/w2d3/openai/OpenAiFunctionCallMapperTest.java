package com.pickagent.w2d3.openai;

import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseReasoningItem;
import com.pickagent.w2.core.AgentDecision;
import com.pickagent.w2.openai.OpenAiFunctionCallMapper;
import com.pickagent.w2.openai.OpenAiFunctionCallMappingException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiFunctionCallMapperTest {
    private final OpenAiFunctionCallMapper mapper = new OpenAiFunctionCallMapper();

    @Test
    void mapsCallIdNameAndStringArgumentsWithoutExecutingATool() {
        AgentDecision.ToolCall mapped = mapper.map(List.of(call(
                "call_order_001", "lookup_order", "{\"orderId\":\"ORD-001\"}")));

        assertEquals("call_order_001", mapped.callId());
        assertEquals("lookup_order", mapped.toolName());
        assertEquals(Map.of("orderId", "ORD-001"), mapped.arguments());
    }

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

    @Test
    void malformedArgumentsJsonFailsExplicitly() {
        var failure = assertThrows(OpenAiFunctionCallMappingException.class,
                () -> mapper.map(List.of(call("call_bad", "lookup_order", "{not-json"))));

        assertEquals(OpenAiFunctionCallMappingException.Reason.MALFORMED_ARGUMENTS_JSON, failure.reason());
        assertEquals("function_call arguments must be valid JSON", failure.getMessage());
        assertNotNull(failure.getCause());
    }

    @Test
    void arrayArgumentsRootFailsExplicitly() {
        assertFailure("[\"ORD-001\"]",
                OpenAiFunctionCallMappingException.Reason.ARGUMENTS_NOT_OBJECT,
                "function_call arguments root must be a JSON object");
    }

    @Test
    void nonStringArgumentValueFailsExplicitly() {
        assertFailure("{\"orderId\":123}",
                OpenAiFunctionCallMappingException.Reason.NON_STRING_ARGUMENT,
                "function_call argument 'orderId' must be a string");
    }

    @Test
    void multipleFunctionCallsFailBeforeEitherCanBeSilentlySelected() {
        var failure = assertThrows(OpenAiFunctionCallMappingException.class, () -> mapper.map(List.of(
                call("call_1", "lookup_order", "{\"orderId\":\"ORD-001\"}"),
                call("call_2", "lookup_order", "{\"orderId\":\"ORD-002\"}"))));

        assertEquals(OpenAiFunctionCallMappingException.Reason.MULTIPLE_FUNCTION_CALLS, failure.reason());
        assertEquals("expected exactly one function_call but found 2", failure.getMessage());
    }

    @Test
    void outputWithoutAFunctionCallFailsExplicitly() {
        ResponseReasoningItem reasoning = ResponseReasoningItem.builder()
                .id("rs_only")
                .summary(List.of())
                .build();

        var failure = assertThrows(OpenAiFunctionCallMappingException.class,
                () -> mapper.map(List.of(ResponseOutputItem.ofReasoning(reasoning))));

        assertEquals(OpenAiFunctionCallMappingException.Reason.NO_FUNCTION_CALL, failure.reason());
        assertEquals("expected exactly one function_call but found 0", failure.getMessage());
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
