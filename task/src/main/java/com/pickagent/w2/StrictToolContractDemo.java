package com.pickagent.w2;

import com.openai.models.responses.FunctionTool;
import com.pickagent.w2.core.AgentDecision;
import com.pickagent.w2.core.AgentRuntime;
import com.pickagent.w2.core.ToolDefinition;
import com.pickagent.w2.core.ToolRegistry;
import com.pickagent.w2.infrastructure.ReplayAgentModel;
import com.pickagent.w2.infrastructure.ReplayOrderTool;
import com.pickagent.w2.openai.OpenAiFunctionToolMapper;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 验证 OpenAI 严格工具 Schema 与 Runtime 执行契约一致性的离线演示。
 *
 * <p>演示同时覆盖合法 Replay 回合和额外参数注入拒绝，不访问网络或读取 API Key。</p>
 *
 * @author jamieLu
 * @since 2026-09-01
 */
public final class StrictToolContractDemo {
    /** 工具类不允许实例化。 */
    private StrictToolContractDemo() {
    }

    /**
     * 运行命令行演示。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        run(System.out);
    }

    /**
     * 验证广告 Schema 与 Registry 字段一致，并执行合法和非法两个离线路径。
     *
     * @param out 演示输出流
     * @throws IllegalStateException Schema 漂移、合法路径未完成或非法路径到达 handler 时抛出
     */
    public static void run(PrintStream out) {
        AtomicInteger validExecutions = new AtomicInteger();
        ToolRegistry validRegistry = registry(validExecutions);
        FunctionTool advertised = new OpenAiFunctionToolMapper()
                .map(validRegistry.definitions().getFirst());
        Map<String, Object> schema = schema(advertised);

        Set<String> propertyNames = objectMap(schema.get("properties")).keySet();
        Set<String> registryNames = validRegistry.definitions().getFirst().requiredArguments();
        List<String> required = stringList(schema.get("required"));
        boolean contractMatches = advertised.strict().orElse(false)
                && "object".equals(schema.get("type"))
                && Boolean.FALSE.equals(schema.get("additionalProperties"))
                && new LinkedHashSet<>(propertyNames).equals(registryNames)
                && required.equals(validRegistry.definitions().getFirst().parameters().stream()
                        .map(ToolDefinition.RequiredStringParameter::name).toList());
        if (!contractMatches) {
            throw new IllegalStateException("advertised schema and registry contract drifted");
        }

        out.println("contract.match=true strict=true properties=" + propertyNames
                + " required=" + required + " additionalProperties=false");

        AgentRuntime.Result valid = new AgentRuntime(
                new ReplayAgentModel(), validRegistry, 3)
                .run("What is the status of order ORD-001?");
        if (!(valid instanceof AgentRuntime.Completed completed) || validExecutions.get() != 1) {
            throw new IllegalStateException("valid replay must complete with exactly one handler execution: " + valid);
        }
        out.println("valid.result=COMPLETED handlerExecutions=" + validExecutions.get()
                + " history=" + completed.history().size());
        out.println("valid.finalAnswer=" + completed.answer().text());

        AtomicInteger invalidExecutions = new AtomicInteger();
        ToolRegistry invalidRegistry = registry(invalidExecutions);
        AgentRuntime.Result invalid = new AgentRuntime(
                context -> new AgentDecision.ToolCall(
                        "call_with_admin", "lookup_order",
                        Map.of("orderId", "ORD-001", "admin", "true")),
                invalidRegistry,
                2).run("Try an undeclared admin argument");
        if (!(invalid instanceof AgentRuntime.Stopped stopped)
                || stopped.reason() != AgentRuntime.StopReason.INVALID_ARGUMENTS
                || invalidExecutions.get() != 0) {
            throw new IllegalStateException("extra argument must be rejected before handler execution: " + invalid);
        }
        out.println("invalid.result=STOPPED reason=" + stopped.reason());
        out.println("invalid.detail=" + stopped.detail());
        out.println("invalid.handlerExecutions=" + invalidExecutions.get());
        out.println("conclusion=Strict schema does not replace Registry validation or grant execution permission.");
    }

    /**
     * 创建带执行次数统计的订单工具注册表。
     *
     * @param executions handler 执行计数器
     * @return 只注册 lookup_order 的工具注册表
     */
    private static ToolRegistry registry(AtomicInteger executions) {
        ReplayOrderTool replay = new ReplayOrderTool();
        return new ToolRegistry(List.of(new ToolRegistry.Registration(
                ReplayOrderTool.DEFINITION,
                arguments -> {
                    executions.incrementAndGet();
                    return replay.execute(arguments);
                })));
    }

    /**
     * 将 SDK FunctionTool 参数的扩展字段转换为便于断言的普通映射。
     *
     * @param tool OpenAI FunctionTool
     * @return 按插入顺序保存的 Schema 字段
     */
    private static Map<String, Object> schema(FunctionTool tool) {
        return tool.parameters().orElseThrow()._additionalProperties().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().convert(Object.class),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    /**
     * 将已知 Schema object 值转换为字符串键映射。
     *
     * @param value Schema object 值
     * @return Schema 映射
     * @throws ClassCastException value 不是预期映射时抛出
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMap(Object value) {
        return (Map<String, Object>) value;
    }


    /**
     * 将已知 required 值转换为字符串列表。
     *
     * @param value required 数组值
     * @return required 字段列表
     * @throws ClassCastException value 不是预期列表时抛出
     */
    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object value) {
        return (List<String>) value;
    }
}
