package com.pickagent.w2d1.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolDefinitionTest {
    @Test
    void metadataRepresentsRequiredStringsAndKeepsDeclaredOrder() {
        var definition = new ToolDefinition("search", "Search records", List.of(
                new ToolDefinition.RequiredStringParameter("query", "Search text"),
                new ToolDefinition.RequiredStringParameter("tenant", "Tenant id")));

        assertEquals(List.of("query", "tenant"),
                definition.parameters().stream().map(ToolDefinition.RequiredStringParameter::name).toList());
        assertEquals(Set.of("query", "tenant"), definition.requiredArguments());
    }

    @Test
    void originalSetConstructorRemainsCompatibleAndDeterministic() {
        var definition = new ToolDefinition("search", "Search records", Set.of("tenant", "query"));

        assertEquals(List.of("query", "tenant"),
                definition.parameters().stream().map(ToolDefinition.RequiredStringParameter::name).toList());
    }

    @Test
    void duplicateParameterNamesAreRejected() {
        var duplicate = new ToolDefinition.RequiredStringParameter("query");

        var error = assertThrows(IllegalArgumentException.class,
                () -> new ToolDefinition("search", "Search records", List.of(duplicate, duplicate)));

        assertEquals("duplicate parameter: query", error.getMessage());
    }
}
