package io.github.jamielu.agent.integration;

import com.openai.models.responses.FunctionTool;
import io.github.jamielu.agent.api.AgentDecision;
import io.github.jamielu.agent.runtime.AgentRuntime;
import io.github.jamielu.agent.api.AgentState;
import io.github.jamielu.agent.tool.ToolRegistry;
import io.github.jamielu.agent.example.support.ReplayAgentModel;
import io.github.jamielu.agent.example.support.ReplayOrderTool;
import io.github.jamielu.agent.openai.OpenAiFunctionToolMapper;
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

class ResponsesToolContractTest {
    private final OpenAiFunctionToolMapper mapper = new OpenAiFunctionToolMapper();

    // 场景：公开 Schema 属性与注册表参数名称完全一致；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void advertisedSchemaPropertiesExactlyMatchRegistryArgumentNames() {
        ToolRegistry registry = registry(new AtomicInteger());
        Map<String, Object> schema = schema(mapper.map(registry.definitions().getFirst()));

        Set<String> properties = new LinkedHashSet<>(objectMap(schema.get("properties")).keySet());

        assertEquals(registry.definitions().getFirst().requiredArguments(), properties);
    }

    // 场景：公开必填顺序与注册表定义顺序一致；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void advertisedRequiredOrderMatchesRegistryDefinitionOrder() {
        ToolRegistry registry = registry(new AtomicInteger());
        var definition = registry.definitions().getFirst();
        Map<String, Object> schema = schema(mapper.map(definition));

        assertEquals(definition.parameters().stream().map(parameter -> parameter.name()).toList(),
                schema.get("required"));
    }

    // 场景：公开契约为仅含字符串属性的严格封闭对象；行为：执行对应代码路径；预期：相关业务断言全部成立。
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

    // 场景：合法订单查询完成整轮回放且处理器执行一次；行为：执行对应代码路径；预期：相关业务断言全部成立。
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

    // 场景：多余管理参数无效且不会到达处理器；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void extraAdminArgumentIsInvalidAndNeverReachesHandler() {
        assertRejectedBeforeExecution(
                Map.of("orderId", "ORD-001", "admin", "true"),
                "invalid arguments for lookup_order: missing=[], extra=[admin]");
    }

    // 场景：缺少必填参数无效且不会到达处理器；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void missingRequiredArgumentIsInvalidAndNeverReachesHandler() {
        assertRejectedBeforeExecution(
                Map.of(),
                "invalid arguments for lookup_order: missing=[orderId], extra=[]");
    }

    // 场景：字符串类型参数为空白时仍被注册表拒绝；行为：执行对应代码路径；预期：相关业务断言全部成立。
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


