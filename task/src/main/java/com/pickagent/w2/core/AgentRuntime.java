package com.pickagent.w2.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 驱动有界“模型决策—工具执行—模型续接”循环的 Agent Runtime。
 *
 * <p>该类维护历史、步骤、状态轨迹和停止策略，不包含供应商协议或网络传输知识。</p>
 *
 * @author jamieLu
 * @since 2026-08-31
 */
public final class AgentRuntime {
    /** 单次模型决策端口。 */
    private final AgentModelPort model;
    /** 工具白名单、参数校验和分发注册表。 */
    private final ToolRegistry tools;
    /** 一次运行允许的最大模型决策步骤数。 */
    private final int maxSteps;

    /**
     * 创建有界 Agent Runtime。
     *
     * <p>一个步骤包含一次模型决策和至多一次串行工具执行。</p>
     *
     * @param model 单次模型决策端口
     * @param tools 工具注册表
     * @param maxSteps 最大步骤数，必须为正数
     * @throws NullPointerException model 或 tools 为 {@code null} 时抛出
     * @throws IllegalArgumentException maxSteps 非正数时抛出
     */
    public AgentRuntime(AgentModelPort model, ToolRegistry tools, int maxSteps) {
        this.model = Objects.requireNonNull(model, "model");
        this.tools = Objects.requireNonNull(tools, "tools");
        if (maxSteps < 1) {
            throw new IllegalArgumentException("maxSteps must be positive");
        }
        this.maxSteps = maxSteps;
    }

    /**
     * 使用给定输入运行一次 Agent，直到完成或命中明确停止条件。
     *
     * @param input 非空白用户输入
     * @return 完成、停止或工具失败结果
     * @throws IllegalArgumentException input 为空时抛出
     * @throws NullPointerException 模型违反端口契约并返回 {@code null} 时抛出
     */
    public Result run(String input) {
        Checks.nonBlank(input, "input");
        List<AgentState> trace = new ArrayList<>(List.of(AgentState.START));
        List<AgentContext.Exchange> history = new ArrayList<>();
        List<AgentStep> steps = new ArrayList<>();
        var executedCallIds = new HashSet<String>();

        for (int step = 1; step <= maxSteps; step++) {
            trace.add(AgentState.MODEL);
            AgentDecision decision = Objects.requireNonNull(
                    model.decide(new AgentContext(input, history, tools.definitions())),
                    "model returned null decision");
            steps.add(new AgentStep(step, decision, Optional.empty()));
            if (decision instanceof AgentDecision.FinalAnswer answer) {
                trace.add(AgentState.FINAL);
                trace.add(AgentState.STOP);
                return new Completed(answer, trace, history, steps);
            }

            var call = (AgentDecision.ToolCall) decision;
            trace.add(AgentState.TOOL);
            if (executedCallIds.contains(call.callId())) {
                return stopped(StopReason.DUPLICATE_CALL_ID,
                        "duplicate callId: " + call.callId(), trace, history, steps);
            }
            // Do not execute a tool if no model turn remains to consume its output.
            if (step == maxSteps) {
                return stopped(StopReason.MAX_STEPS,
                        "maxSteps reached before tool execution: " + maxSteps, trace, history, steps);
            }

            ToolResult result;
            try {
                result = tools.execute(call);
            } catch (ToolRegistry.RejectedCall rejected) {
                StopReason reason = switch (rejected.reason()) {
                    case UNKNOWN_TOOL -> StopReason.UNKNOWN_TOOL;
                    case INVALID_ARGUMENTS -> StopReason.INVALID_ARGUMENTS;
                };
                return stopped(reason, rejected.getMessage(), trace, history, steps);
            } catch (ToolExecutionException failure) {
                trace.add(AgentState.STOP);
                return new ToolFailed(call, failure, trace, history, steps);
            }
            executedCallIds.add(call.callId());
            history.add(new AgentContext.Exchange(call, result));
            steps.set(steps.size() - 1, new AgentStep(step, decision, Optional.of(result)));
        }
        throw new AssertionError("bounded loop must return at its final turn");
    }

    /**
     * 追加 STOP 状态并创建不可变停止结果。
     *
     * @param reason 停止原因
     * @param detail 停止详情
     * @param trace 当前状态轨迹
     * @param history 已成功执行的工具交互历史
     * @param steps 已产生的步骤
     * @return 停止结果
     */
    private static Stopped stopped(StopReason reason, String detail,
                                   List<AgentState> trace, List<AgentContext.Exchange> history,
                                   List<AgentStep> steps) {
        trace.add(AgentState.STOP);
        return new Stopped(reason, detail, trace, history, steps);
    }

    /**
     * 一次 Agent 运行的供应商中立终态结果。
     */
    public sealed interface Result permits Completed, Stopped, ToolFailed {
        /**
         * 返回完整生命周期轨迹。
         *
         * @return 不可修改状态列表
         */
        List<AgentState> trace();

        /**
         * 返回成功完成的工具调用与结果历史。
         *
         * @return 不可修改交互历史
         */
        List<AgentContext.Exchange> history();

        /**
         * 返回每一次模型决策及其可选观察。
         *
         * @return 不可修改步骤列表
         */
        List<AgentStep> steps();

        /**
         * 返回已经消耗的模型决策步骤数。
         *
         * @return 步骤数量
         */
        default int stepsTaken() {
            return steps().size();
        }
    }

    /**
     * 模型成功产生最终回答的运行结果。
     *
     * @param answer 最终回答
     * @param trace 完整状态轨迹
     * @param history 成功工具交互历史
     * @param steps 全部模型决策步骤
     */
    public record Completed(AgentDecision.FinalAnswer answer, List<AgentState> trace,
                            List<AgentContext.Exchange> history, List<AgentStep> steps) implements Result {
        /**
         * 创建完成结果并复制所有集合快照。
         *
         * @throws NullPointerException answer 或集合及其元素为 {@code null} 时抛出
         */
        public Completed {
            Objects.requireNonNull(answer, "answer");
            trace = List.copyOf(trace);
            history = List.copyOf(history);
            steps = List.copyOf(steps);
        }
    }

    /**
     * 因安全边界或输入拒绝而停止、且未产生最终回答的结果。
     *
     * @param reason 停止原因
     * @param detail 可审计停止详情
     * @param trace 完整状态轨迹
     * @param history 停止前的成功工具交互历史
     * @param steps 停止前已产生的步骤
     */
    public record Stopped(StopReason reason, String detail, List<AgentState> trace,
                          List<AgentContext.Exchange> history, List<AgentStep> steps) implements Result {
        /**
         * 创建停止结果并复制所有集合快照。
         *
         * @throws IllegalArgumentException detail 为空时抛出
         * @throws NullPointerException reason 或集合及其元素为 {@code null} 时抛出
         */
        public Stopped {
            Objects.requireNonNull(reason, "reason");
            Checks.nonBlank(detail, "stop detail");
            trace = List.copyOf(trace);
            history = List.copyOf(history);
            steps = List.copyOf(steps);
        }
    }

    /**
     * 工具 adapter 已明确分类的可预期执行失败结果。
     *
     * <p>该结果区别于参数校验/预算停止和模型最终回答。</p>
     *
     * @param call 执行失败的工具调用
     * @param failure 已分类工具异常
     * @param trace 完整状态轨迹
     * @param history 失败前的成功工具交互历史
     * @param steps 包含失败决策的步骤列表
     */
    public record ToolFailed(AgentDecision.ToolCall call, ToolExecutionException failure,
                             List<AgentState> trace, List<AgentContext.Exchange> history,
                             List<AgentStep> steps) implements Result {
        /**
         * 创建工具失败结果并复制所有集合快照。
         *
         * @throws NullPointerException call、failure 或集合及其元素为 {@code null} 时抛出
         */
        public ToolFailed {
            Objects.requireNonNull(call, "call");
            Objects.requireNonNull(failure, "failure");
            trace = List.copyOf(trace);
            history = List.copyOf(history);
            steps = List.copyOf(steps);
        }
    }

    /** Agent Runtime 主动停止的分类原因。 */
    public enum StopReason {
        /** 模型请求了未注册工具。 */
        UNKNOWN_TOOL,
        /** 工具参数缺失、多余或违反非空白约束。 */
        INVALID_ARGUMENTS,
        /** 已耗尽最大步骤预算。 */
        MAX_STEPS,
        /** 同一次运行中重复出现已执行的 callId。 */
        DUPLICATE_CALL_ID
    }
}
