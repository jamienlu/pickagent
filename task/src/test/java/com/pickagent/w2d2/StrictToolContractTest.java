package com.pickagent.w2d2;

import com.openai.models.responses.FunctionTool;
import com.pickagent.w2.core.AgentDecision;
import com.pickagent.w2.core.AgentRuntime;
import com.pickagent.w2.core.AgentState;
import com.pickagent.w2.core.ToolRegistry;
import com.pickagent.w2.infrastructure.ReplayAgentModel;
import com.pickagent.w2.infrastructure.ReplayOrderTool;
import com.pickagent.w2.openai.OpenAiFunctionToolMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrictToolContractTest {
    private final OpenAiFunctionToolMapper mapper = new OpenAiFunctionToolMapper();

    @Test
    void advertisedSchemaPropertiesExactlyMatchRegistryArgumentNames() {
        ToolRegistry registry = registry(new AtomicInteger());
        Map<String, Object> schema = schema(mapper.map(registry.definitions().getFirst()));

        Set<String> properties = new LinkedHashSet<>(objectMap(schema.get("properties")).keySet());

        assertEquals(registry.definitions().getFirst().requiredArguments(), properties);
    }

    @Test
    void advertisedRequiredOrderMatchesRegistryDefinitionOrder() {
        ToolRegistry registry = registry(new AtomicInteger());
        var definition = registry.definitions().getFirst();
        Map<String, Object> schema = schema(mapper.map(definition));

        assertEquals(definition.parameters().stream().map(parameter -> parameter.name()).toList(),
                schema.get("required"));
    }

    @Test
    void advertisedContractIsStrictClosedObjectWithOnlyStringProperties() {
        FunctionTool tool = mapper.map(ReplayOrderTool.DEFINITION);
        Map<String, Object> schema = schema(tool);

        assertTrue(tool.strict().orElseThrow());
        assertEquals("object", schema.get("type"));
        assertEquals(false, schema.get("additionalProperties"));
        objectMap(schema.get("properties")).values().forEach(property ->
                assertEquals("string", objectMap(property).get("type")));
    }

    @Test
    void validLookupOrderCompletesOneFullReplayRoundAndExecutesHandlerOnce() {
        AtomicInteger handlerExecutions = new AtomicInteger();
        var result = new AgentRuntime(new ReplayAgentModel(), registry(handlerExecutions), 3)
                .run("What is the status of order ORD-001?");

        var completed = assertInstanceOf(AgentRuntime.Completed.class, result);
        assertEquals("Replay answer: Order ORD-001: SHIPPED", completed.answer().text());
        assertEquals(1, completed.history().size());
        assertEquals(1, handlerExecutions.get());
        assertEquals(List.of(AgentState.START, AgentState.MODEL, AgentState.TOOL,
                AgentState.MODEL, AgentState.FINAL, AgentState.STOP), completed.trace());
    }

    @Test
    void extraAdminArgumentIsInvalidAndNeverReachesHandler() {
        assertRejectedBeforeExecution(
                Map.of("orderId", "ORD-001", "admin", "true"),
                "invalid arguments for lookup_order: missing=[], extra=[admin]");
    }

    @Test
    void missingRequiredArgumentIsInvalidAndNeverReachesHandler() {
        assertRejectedBeforeExecution(
                Map.of(),
                "invalid arguments for lookup_order: missing=[orderId], extra=[]");
    }

    @Test
    void blankStringIsRejectedByRegistryEvenThoughItHasSchemaStringType() {
        assertRejectedBeforeExecution(Map.of("orderId", " "), "blank argument: orderId");
    }

    private static void assertRejectedBeforeExecution(Map<String, String> arguments, String expectedDetail) {
        AtomicInteger handlerExecutions = new AtomicInteger();
        AgentRuntime.Result result = new AgentRuntime(
                context -> new AgentDecision.ToolCall("contract_call", "lookup_order", arguments),
                registry(handlerExecutions),
                2).run("Validate this call");

        var stopped = assertInstanceOf(AgentRuntime.Stopped.class, result);
        assertEquals(AgentRuntime.StopReason.INVALID_ARGUMENTS, stopped.reason());
        assertEquals(expectedDetail, stopped.detail());
        assertEquals(0, handlerExecutions.get());
        assertTrue(stopped.history().isEmpty());
    }

    private static ToolRegistry registry(AtomicInteger handlerExecutions) {
        ReplayOrderTool replay = new ReplayOrderTool();
        return new ToolRegistry(List.of(new ToolRegistry.Registration(
                ReplayOrderTool.DEFINITION,
                arguments -> {
                    handlerExecutions.incrementAndGet();
                    return replay.execute(arguments);
                })));
    }

    private static Map<String, Object> schema(FunctionTool tool) {
        return tool.parameters().orElseThrow()._additionalProperties().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().convert(Object.class),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMap(Object value) {
        return (Map<String, Object>) value;
    }
}
