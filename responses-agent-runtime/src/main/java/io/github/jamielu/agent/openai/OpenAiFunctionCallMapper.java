package io.github.jamielu.agent.openai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseOutputItem;
import io.github.jamielu.agent.api.AgentDecision;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;

/**
 * Maps OpenAI Responses function calls into provider-neutral calls.
 *
 * <p>Non-function output items, including reasoning items, are traversed but not represented by the current
 * core type. This mapper parses provider JSON only; it neither validates a registered tool contract nor executes
 * a tool.</p>
 *
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
        List<AgentDecision.ToolCall> calls = mapAll(outputItems);
        if (calls.size() != 1) {
            throw new OpenAiFunctionCallMappingException(
                    OpenAiFunctionCallMappingException.Reason.MULTIPLE_FUNCTION_CALLS,
                    "expected exactly one function_call but found " + calls.size());
        }
        return calls.getFirst();
    }

    /**
     * Maps every function call in response order without executing a tool.
     *
     * @param outputItems heterogeneous Responses output items
     * @return immutable, non-empty call list
     */
    public List<AgentDecision.ToolCall> mapAll(List<ResponseOutputItem> outputItems) {
        Objects.requireNonNull(outputItems, "outputItems");
        List<ResponseFunctionToolCall> sdkCalls = new ArrayList<>();
        for (ResponseOutputItem item : List.copyOf(outputItems)) {
            item.functionCall().ifPresent(sdkCalls::add);
        }
        if (sdkCalls.isEmpty()) {
            throw new OpenAiFunctionCallMappingException(
                    OpenAiFunctionCallMappingException.Reason.NO_FUNCTION_CALL,
                    "expected at least one function_call but found 0");
        }

        var callIds = new HashSet<String>();
        var calls = new ArrayList<AgentDecision.ToolCall>(sdkCalls.size());
        for (ResponseFunctionToolCall call : sdkCalls) {
            AgentDecision.ToolCall mapped = mapCall(call);
            if (!callIds.add(mapped.callId())) {
                throw new OpenAiFunctionCallMappingException(
                        OpenAiFunctionCallMappingException.Reason.DUPLICATE_CALL_ID,
                        "duplicate function_call call_id: " + mapped.callId());
            }
            calls.add(mapped);
        }
        return List.copyOf(calls);
    }

    private static AgentDecision.ToolCall mapCall(ResponseFunctionToolCall call) {
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


