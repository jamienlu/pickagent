package io.github.jamielu.agent.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolDefinitionTest {
    // 场景：工具元数据表达必填字符串并保持声明顺序；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void metadataRepresentsRequiredStringsAndKeepsDeclaredOrder() {
        var definition = new ToolDefinition("search", "Search records", List.of(
                new ToolDefinition.RequiredStringParameter("query", "Search text"),
                new ToolDefinition.RequiredStringParameter("tenant", "Tenant id")));

        assertEquals(List.of("query", "tenant"),
                definition.parameters().stream().map(ToolDefinition.RequiredStringParameter::name).toList());
        assertEquals(Set.of("query", "tenant"), definition.requiredArguments());
    }

    // 场景：原集合构造器保持兼容且结果确定；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void originalSetConstructorRemainsCompatibleAndDeterministic() {
        var definition = new ToolDefinition("search", "Search records", Set.of("tenant", "query"));

        assertEquals(List.of("query", "tenant"),
                definition.parameters().stream().map(ToolDefinition.RequiredStringParameter::name).toList());
    }

    // 场景：重复参数名称在工具定义阶段被拒绝；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void duplicateParameterNamesAreRejected() {
        var duplicate = new ToolDefinition.RequiredStringParameter("query");

        var error = assertThrows(IllegalArgumentException.class,
                () -> new ToolDefinition("search", "Search records", List.of(duplicate, duplicate)));

        assertEquals("duplicate parameter: query", error.getMessage());
    }
}


