package io.github.jamielu.agent.openai;

import com.openai.models.responses.FunctionTool;
import io.github.jamielu.agent.api.ToolDefinition;
import io.github.jamielu.agent.openai.OpenAiFunctionToolMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiFunctionToolMapperTest {
    private final OpenAiFunctionToolMapper mapper = new OpenAiFunctionToolMapper();

    // 场景：工具映射保留名称说明并启用严格模式；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void mapsNameDescriptionAndEnablesStrictMode() {
        FunctionTool tool = mapper.map(definition());

        assertEquals("search_records", tool.name());
        assertEquals("Search tenant records", tool.description().orElseThrow());
        assertTrue(tool.strict().orElseThrow());
    }

    // 场景：工具 Schema 根对象关闭额外属性；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void buildsClosedObjectAtTheSchemaRoot() {
        Map<String, Object> schema = schema(mapper.map(definition()));

        assertEquals("object", schema.get("type"));
        assertEquals(false, schema.get("additionalProperties"));
    }

    // 场景：每个核心参数映射为字符串属性；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void mapsEveryParameterAsAStringProperty() {
        Map<String, Object> properties = objectMap(schema(mapper.map(definition())).get("properties"));

        assertEquals(List.of("tenant", "query"), List.copyOf(properties.keySet()));
        assertEquals("string", objectMap(properties.get("tenant")).get("type"));
        assertEquals("Tenant id", objectMap(properties.get("tenant")).get("description"));
        assertEquals("string", objectMap(properties.get("query")).get("type"));
        assertFalse(objectMap(properties.get("query")).containsKey("items"));
    }

    // 场景：全部属性按声明顺序进入必填列表；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void putsAllPropertiesInRequiredUsingDeclaredOrder() {
        Map<String, Object> schema = schema(mapper.map(definition()));

        assertEquals(List.of("tenant", "query"), schema.get("required"));
    }

    // 场景：兼容集合构造器产生稳定必填顺序；行为：执行对应代码路径；预期：相关业务断言全部成立。
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


