package com.pickagent.w2.openai;

import com.openai.core.JsonValue;
import com.openai.models.responses.FunctionTool;
import com.pickagent.w2.core.ToolDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** OpenAI-specific adapter from the provider-neutral tool contract to FunctionTool. */
public final class OpenAiFunctionToolMapper {
    public FunctionTool map(ToolDefinition definition) {
        Objects.requireNonNull(definition, "definition");

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (ToolDefinition.RequiredStringParameter parameter : definition.parameters()) {
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("type", "string");
            if (!parameter.description().isBlank()) {
                property.put("description", parameter.description());
            }
            properties.put(parameter.name(), property);
            required.add(parameter.name());
        }

        FunctionTool.Parameters parameters = FunctionTool.Parameters.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(properties))
                .putAdditionalProperty("required", JsonValue.from(List.copyOf(required)))
                .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                .build();

        return FunctionTool.builder()
                .name(definition.name())
                .description(definition.description())
                .parameters(parameters)
                .strict(true)
                .build();
    }
}
