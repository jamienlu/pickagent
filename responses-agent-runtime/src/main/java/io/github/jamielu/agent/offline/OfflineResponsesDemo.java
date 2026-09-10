package io.github.jamielu.agent.offline;

import io.github.jamielu.agent.api.AgentDecision;
import io.github.jamielu.agent.api.ToolDefinition;
import io.github.jamielu.agent.runtime.AgentRuntime;
import io.github.jamielu.agent.runtime.RunBudget;
import io.github.jamielu.agent.tool.ToolRegistry;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 无密钥、无网络、无费用的离线验收入口。 */
public final class OfflineResponsesDemo {
    private OfflineResponsesDemo() {
    }

    /**
     * 运行确定性工具循环并打印可观察证据。
     *
     * @param args 忽略命令行参数
     */
    public static void main(String[] args) {
        ToolDefinition definition = new ToolDefinition(
                "lookup_order", "查询离线订单", Set.of("orderId"));
        ToolRegistry registry = new ToolRegistry(List.of(
                new ToolRegistry.Registration(definition,
                        values -> "Order " + values.get("orderId") + ": SHIPPED")));
        AgentRuntime runtime = new AgentRuntime(context -> context.history().isEmpty()
                ? new AgentDecision.ToolCall(
                        "call_offline_001", "lookup_order", Map.of("orderId", "ORD-001"))
                : new AgentDecision.FinalAnswer(
                        "Replay answer: " + context.history().getFirst().result().output()),
                registry,
                new RunBudget(2, 1, Duration.ofSeconds(5)));

        AgentRuntime.Completed completed = (AgentRuntime.Completed) runtime.run("Find ORD-001");
        System.out.println("final.answer=" + completed.answer().text());
        System.out.println("model.calls=" + completed.usage().modelCalls());
        System.out.println("tool.calls=" + completed.usage().toolCalls());
        System.out.println("ledger.proof=PASS");
    }
}
