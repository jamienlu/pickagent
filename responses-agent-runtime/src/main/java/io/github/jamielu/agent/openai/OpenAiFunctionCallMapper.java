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

/** 将 OpenAI Responses 函数调用映射为供应商中立调用。 */
public final class OpenAiFunctionCallMapper {
    /** 创建无状态入站映射器。 */
    public OpenAiFunctionCallMapper() {
    }

    /**
     * 查找唯一函数调用并转换其纯字符串 JSON 参数。
     *
     * @param outputItems Responses 异构输出条目
     * @return 唯一供应商中立工具调用
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
     * 按响应顺序映射全部函数调用且不执行工具。
     *
     * @param outputItems Responses 异构输出条目
     * @return 按响应顺序排列的不可变工具调用集合
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
        if (encodedArguments.isBlank()) {
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


