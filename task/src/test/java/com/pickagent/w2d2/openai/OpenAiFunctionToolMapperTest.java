package com.pickagent.w2d2.openai;

import com.openai.models.responses.FunctionTool;
import com.pickagent.w2.core.ToolDefinition;
import com.pickagent.w2.openai.OpenAiFunctionToolMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiFunctionToolMapperTest {
    private final OpenAiFunctionToolMapper mapper = new OpenAiFunctionToolMapper();

    @Test
    void mapsNameDescriptionAndEnablesStrictMode() {
        FunctionTool tool = mapper.map(definition());

        assertEquals("search_records", tool.name());
        assertEquals("Search tenant records", tool.description().orElseThrow());
        assertTrue(tool.strict().orElseThrow());
    }

    @Test
    void buildsClosedObjectAtTheSchemaRoot() {
        Map<String, Object> schema = schema(mapper.map(definition()));

        assertEquals("object", schema.get("type"));
        assertEquals(false, schema.get("additionalProperties"));
    }

    @Test
    void mapsEveryParameterAsAStringProperty() {
        Map<String, Object> properties = objectMap(schema(mapper.map(definition())).get("properties"));

        assertEquals(List.of("tenant", "query"), List.copyOf(properties.keySet()));
        assertEquals("string", objectMap(properties.get("tenant")).get("type"));
        assertEquals("Tenant id", objectMap(properties.get("tenant")).get("description"));
        assertEquals("string", objectMap(properties.get("query")).get("type"));
        assertFalse(objectMap(properties.get("query")).containsKey("items"));
    }

    @Test
    void putsAllPropertiesInRequiredUsingDeclaredOrder() {
        Map<String, Object> schema = schema(mapper.map(definition()));

        assertEquals(List.of("tenant", "query"), schema.get("required"));
    }

    @Test
    void compatibleSetConstructorProducesStableRequiredOrder() {
        var legacy = new ToolDefinition("search_records", "Search tenant records", Set.of("tenant", "query"));

        assertEquals(List.of("query", "tenant"), schema(mapper.map(legacy)).get("required"));
    }

    private static ToolDefinition definition() {
        return new ToolDefinition("search_records", "Search tenant records", List.of(
                new ToolDefinition.RequiredStringParameter("tenant", "Tenant id"),
                new ToolDefinition.RequiredStringParameter("query")));
    }

    private static Map<String, Object> schema(FunctionTool tool) {
        return tool.parameters().orElseThrow()._additionalProperties().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().convert(Object.class),
                        (left, right) -> left,
                        java.util.LinkedHashMap::new));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMap(Object value) {
        return (Map<String, Object>) value;
    }
}
