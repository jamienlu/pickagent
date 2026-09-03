package com.pickagent.w2.openai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseOutputItem;
import com.pickagent.w2.core.AgentDecision;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Maps one OpenAI Responses function call into the provider-neutral single-call decision.
 *
 * <p>Non-function output items, including reasoning items, are traversed but not represented by the current
 * core type. This mapper parses provider JSON only; it neither validates a registered tool contract nor executes
 * a tool.</p>
 *
 * @author jamieLu
 * @since 2026-09-02
 */
public final class OpenAiFunctionCallMapper {
    /** Creates a stateless inbound mapper. */
    public OpenAiFunctionCallMapper() {
    }

    /**
     * Finds exactly one function call and converts its string-only JSON arguments.
     *
     * @param outputItems heterogeneous Responses output items
     * @return provider-neutral tool-call decision
     * @throws NullPointerException when outputItems or an item is null
     * @throws OpenAiFunctionCallMappingException when the output cannot be represented safely
     */
    public AgentDecision.ToolCall map(List<ResponseOutputItem> outputItems) {
        Objects.requireNonNull(outputItems, "outputItems");
        List<ResponseFunctionToolCall> calls = new ArrayList<>();
        for (ResponseOutputItem item : List.copyOf(outputItems)) {
            item.functionCall().ifPresent(calls::add);
        }
        if (calls.isEmpty()) {
            throw new OpenAiFunctionCallMappingException(
                    OpenAiFunctionCallMappingException.Reason.NO_FUNCTION_CALL,
                    "expected exactly one function_call but found 0");
        }
        if (calls.size() > 1) {
            throw new OpenAiFunctionCallMappingException(
                    OpenAiFunctionCallMappingException.Reason.MULTIPLE_FUNCTION_CALLS,
                    "expected exactly one function_call but found " + calls.size());
        }

        ResponseFunctionToolCall call = calls.getFirst();
        if (call.callId().isBlank()) {
            throw invalidField("call_id");
        }
        if (call.name().isBlank()) {
            throw invalidField("name");
        }
        return new AgentDecision.ToolCall(call.callId(), call.name(), parseStringArguments(call.arguments()));
    }

    private static Map<String, String> parseStringArguments(String encodedArguments) {
        if (encodedArguments == null || encodedArguments.isBlank()) {
            throw malformedArguments(null);
        }
        Object parsed;
        try {
            parsed = JSON.parse(encodedArguments);
        } catch (JSONException parseFailure) {
            throw malformedArguments(parseFailure);
        }
        if (!(parsed instanceof JSONObject object)) {
            throw new OpenAiFunctionCallMappingException(
                    OpenAiFunctionCallMappingException.Reason.ARGUMENTS_NOT_OBJECT,
                    "function_call arguments root must be a JSON object");
        }

        Map<String, String> arguments = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : object.entrySet()) {
            if (!(entry.getValue() instanceof String value)) {
                throw new OpenAiFunctionCallMappingException(
                        OpenAiFunctionCallMappingException.Reason.NON_STRING_ARGUMENT,
                        "function_call argument '" + entry.getKey() + "' must be a string");
            }
            arguments.put(entry.getKey(), value);
        }
        return arguments;
    }

    private static OpenAiFunctionCallMappingException invalidField(String field) {
        return new OpenAiFunctionCallMappingException(
                OpenAiFunctionCallMappingException.Reason.INVALID_FUNCTION_CALL_FIELD,
                "function_call " + field + " must not be blank");
    }

    private static OpenAiFunctionCallMappingException malformedArguments(Throwable cause) {
        String message = "function_call arguments must be valid JSON";
        return cause == null
                ? new OpenAiFunctionCallMappingException(
                        OpenAiFunctionCallMappingException.Reason.MALFORMED_ARGUMENTS_JSON, message)
                : new OpenAiFunctionCallMappingException(
                        OpenAiFunctionCallMappingException.Reason.MALFORMED_ARGUMENTS_JSON, message, cause);
    }
}
