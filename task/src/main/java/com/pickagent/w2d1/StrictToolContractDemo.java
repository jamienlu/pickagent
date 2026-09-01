package com.pickagent.w2d1;

import com.openai.models.responses.FunctionTool;
import com.pickagent.w2d1.core.AgentDecision;
import com.pickagent.w2d1.core.AgentRuntime;
import com.pickagent.w2d1.core.ToolRegistry;
import com.pickagent.w2d1.infrastructure.ReplayAgentModel;
import com.pickagent.w2d1.infrastructure.ReplayOrderTool;
import com.pickagent.w2d1.openai.OpenAiFunctionToolMapper;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Offline proof that the advertised strict schema and execution-time registry share one contract. */
public final class StrictToolContractDemo {
    private StrictToolContractDemo() {
    }

    public static void main(String[] args) {
        run(System.out);
    }

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
                        .map(parameter -> parameter.name()).toList());
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

    private static ToolRegistry registry(AtomicInteger executions) {
        ReplayOrderTool replay = new ReplayOrderTool();
        return new ToolRegistry(List.of(new ToolRegistry.Registration(
                ReplayOrderTool.DEFINITION,
                arguments -> {
                    executions.incrementAndGet();
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

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object value) {
        return (List<String>) value;
    }
}
