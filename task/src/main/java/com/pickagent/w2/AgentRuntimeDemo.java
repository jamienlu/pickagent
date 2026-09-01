package com.pickagent.w2;

import com.pickagent.w2.core.AgentDecision;
import com.pickagent.w2.core.AgentRuntime;
import com.pickagent.w2.core.ToolRegistry;
import com.pickagent.w2.infrastructure.ReplayAgentModel;
import com.pickagent.w2.infrastructure.ReplayOrderTool;

import java.io.PrintStream;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 演示单工具 Agent Runtime 的完整离线执行轨迹。
 *
 * <p>该演示使用 Replay 模型和本地工具，不访问网络，也不读取任何供应商凭据。</p>
 *
 * @author jamieLu
 * @since 2026-08-31
 */
public final class AgentRuntimeDemo {
    /** 工具类不允许实例化。 */
    private AgentRuntimeDemo() {
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
     * 执行离线 Agent 回合，并将决策、工具观察和状态轨迹写入指定输出流。
     *
     * @param out 演示输出流
     * @throws NullPointerException 模型、工具或输出流违反非空契约时抛出
     * @throws IllegalStateException Replay 未按预期完成时抛出
     */
    public static void run(PrintStream out) {
        var registry = new ToolRegistry(List.of(
                new ToolRegistry.Registration(ReplayOrderTool.DEFINITION, new ReplayOrderTool())));
        var runtime = new AgentRuntime(new ReplayAgentModel(), registry, 3);
        AgentRuntime.Result result = runtime.run("What is the status of order ORD-001?");
        for (var step : result.steps()) {
            if (step.decision() instanceof AgentDecision.ToolCall call) {
                out.println("step=" + step.number() + " decision=ToolCall");
                out.println("  tool call: callId=" + call.callId()
                        + ", tool=" + call.toolName() + ", arguments=" + call.arguments());
                step.observation().ifPresent(observation -> out.println(
                        "  observation: callId=" + observation.callId() + ", output=" + observation.output()));
            } else {
                var answer = (AgentDecision.FinalAnswer) step.decision();
                out.println("step=" + step.number() + " decision=FinalAnswer");
                out.println("  final answer: " + answer.text());
            }
        }
        out.println("TRACE: " + result.trace().stream()
                .map(Enum::name).collect(Collectors.joining(" -> ")));
        if (!(result instanceof AgentRuntime.Completed completed)) {
            throw new IllegalStateException("Replay must complete: " + result);
        }
        out.println("result=Completed steps=" + completed.stepsTaken());
    }
}
