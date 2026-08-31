package com.pickagent.w1d4;

import com.openai.core.JsonValue;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;

import java.util.List;
import java.util.Map;

/**
 * 创建 Event 严格 JSON Schema 及其 OpenAI SDK 表示。
 *
 * @author jamieLu
 * @since 2026-08-27
 */
public final class EventSchemaFactory {
    private EventSchemaFactory() {
    }

    /**
     * 创建供应商无关的 Event JSON Schema。
     *
     * @return 包含完整必需字段且禁止额外属性的 Schema Map
     */
    public static Map<String, Object> create() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string"),
                        "date", Map.of("type", "string"),
                        "participants", Map.of(
                                "type", "array",
                                "items", Map.of("type", "string")
                        )
                ),
                "required", List.of("name", "date", "participants"),
                "additionalProperties", false
        );
    }

    /**
     * 将 Event Schema 转换为 OpenAI Java SDK 接受的类型。
     *
     * @return SDK JSON Schema 对象
     */
    public static ResponseFormatTextJsonSchemaConfig.Schema createSdkSchema() {
        return JsonValue.from(create())
                .convert(ResponseFormatTextJsonSchemaConfig.Schema.class);
    }
}
