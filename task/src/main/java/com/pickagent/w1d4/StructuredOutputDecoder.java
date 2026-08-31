package com.pickagent.w1d4;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 对模型返回的 Event JSON 执行防御式解析和精确字段校验。
 *
 * <p>该解码器不依赖 JSON 库的宽松映射默认值，而是显式检查顶层类型、字段集合、字段类型和数组元素。</p>
 *
 * @author jamieLu
 * @since 2026-08-27
 */
public final class StructuredOutputDecoder {
    /** Event 契约允许且必须出现的字段集合。 */
    private static final Set<String> EXPECTED_FIELDS = Set.of("name", "date", "participants");

    /** 创建 Event 结构化输出解码器。 */
    public StructuredOutputDecoder() {
    }

    /**
     * 将模型文本解码为 Event 或稳定的失败结果。
     *
     * @param text 模型输出文本
     * @return 成功事件或带错误分类的失败结果
     */
    public DecodeResult decode(String text) {
        if (text == null || text.isBlank()) {
            return failure(DecodeResult.ErrorCode.EMPTY_TEXT, "Response text is null, empty, or blank");
        }

        Object parsed;
        try {
            parsed = JSON.parse(text);
        } catch (JSONException exception) {
            return failure(DecodeResult.ErrorCode.MALFORMED_JSON,
                    "Response text is not valid JSON: " + exception.getMessage());
        }

        if (!(parsed instanceof JSONObject object)) {
            return failure(DecodeResult.ErrorCode.NOT_JSON_OBJECT,
                    "Expected a JSON object but received " + jsonType(parsed));
        }

        Set<String> actualFields = new LinkedHashSet<>(object.keySet());
        Set<String> missingFields = new LinkedHashSet<>(EXPECTED_FIELDS);
        missingFields.removeAll(actualFields);
        if (!missingFields.isEmpty()) {
            return failure(DecodeResult.ErrorCode.MISSING_FIELDS,
                    "Missing required field(s): " + missingFields);
        }

        Set<String> extraFields = new LinkedHashSet<>(actualFields);
        extraFields.removeAll(EXPECTED_FIELDS);
        if (!extraFields.isEmpty()) {
            return failure(DecodeResult.ErrorCode.EXTRA_FIELDS,
                    "Unexpected field(s): " + extraFields);
        }

        Object nameValue = object.get("name");
        if (!(nameValue instanceof String name)) {
            return wrongType("name", "string", nameValue);
        }

        Object dateValue = object.get("date");
        if (!(dateValue instanceof String date)) {
            return wrongType("date", "string", dateValue);
        }

        Object participantsValue = object.get("participants");
        if (!(participantsValue instanceof JSONArray participantsArray)) {
            return wrongType("participants", "array", participantsValue);
        }

        List<String> participants = new ArrayList<>(participantsArray.size());
        for (int index = 0; index < participantsArray.size(); index++) {
            Object participant = participantsArray.get(index);
            if (!(participant instanceof String value)) {
                return wrongType("participants[" + index + "]", "string", participant);
            }
            participants.add(value);
        }

        try {
            return new DecodeResult.Success(new Event(name, date, participants));
        } catch (RuntimeException exception) {
            return failure(DecodeResult.ErrorCode.CONVERSION_FAILED,
                    "Validated JSON could not be converted to Event: " + exception.getMessage());
        }
    }

    /**
     * 创建字段类型错误结果。
     *
     * @param field 字段路径
     * @param expectedType 期望的 JSON 类型
     * @param actualValue 实际值
     * @return 字段类型错误
     */
    private static DecodeResult.Failure wrongType(String field, String expectedType, Object actualValue) {
        return failure(DecodeResult.ErrorCode.WRONG_FIELD_TYPE,
                "Field '" + field + "' must be " + expectedType + " but was " + jsonType(actualValue));
    }

    /**
     * 创建统一的解码失败结果。
     *
     * @param code 错误分类
     * @param reason 失败原因
     * @return 解码失败结果
     */
    private static DecodeResult.Failure failure(DecodeResult.ErrorCode code, String reason) {
        return new DecodeResult.Failure(code, reason);
    }

    /**
     * 获取适合错误信息展示的 JSON 类型名称。
     *
     * @param value JSON 解析值
     * @return 稳定的类型名称
     */
    private static String jsonType(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof JSONObject) {
            return "object";
        }
        if (value instanceof JSONArray) {
            return "array";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        return value.getClass().getSimpleName();
    }
}
