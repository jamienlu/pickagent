package com.pickagent.w1d4;

import com.openai.core.JsonValue;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;

import java.util.List;
import java.util.Map;

public final class EventSchemaFactory {
    private EventSchemaFactory() {
    }

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

    public static ResponseFormatTextJsonSchemaConfig.Schema createSdkSchema() {
        return JsonValue.from(create())
                .convert(ResponseFormatTextJsonSchemaConfig.Schema.class);
    }
}
