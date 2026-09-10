package io.github.jamielu.agent.runtime;

import io.github.jamielu.agent.api.AgentContext;
import io.github.jamielu.agent.api.AgentDecision;
import io.github.jamielu.agent.api.AgentState;
import io.github.jamielu.agent.api.AgentStep;
import io.github.jamielu.agent.api.ToolResult;
import io.github.jamielu.agent.internal.Arguments;
import io.github.jamielu.agent.tool.ToolExecutionException;
import io.github.jamielu.agent.tool.ToolRegistry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 驱动有界“模型决策—工具执行—模型续接”循环的供应商中立运行时。
 *
 * <p>运行时只负责会话生命周期、全局预算和终态归一化；供应商协议、网络传输、
 * 工具业务逻辑和重试策略均位于各自适配边界。</p>
 */
public final class AgentRuntime {
    private final Supplier<? extends AgentModelPort> modelFactory;
    private final ToolRegistry tools;
    private final RunBudget budget;
    private final NanoClock clock;

    /**
     * 使用旧版最大步骤参数创建运行时。
     *
     * @param model 模型决策端口
     * @param tools 工具注册表
     * @param maxSteps 最大模型调用次数
     */
    public AgentRuntime(AgentModelPort model, ToolRegistry tools, int maxSteps) {
        this(singletonFactory(model), tools, RunBudget.fromMaxSteps(maxSteps), NanoClock.system());
    }

    /**
     * 使用完整运行预算创建运行时。
     *
     * @param model 模型决策端口
     * @param tools 工具注册表
     * @param budget 单次运行预算
     */
    public AgentRuntime(AgentModelPort model, ToolRegistry tools, RunBudget budget) {
        this(singletonFactory(model), tools, budget, NanoClock.system());
    }

    private AgentRuntime(Supplier<? extends AgentModelPort> modelFactory,
                         ToolRegistry tools, RunBudget budget, NanoClock clock) {
        this.modelFactory = Objects.requireNonNull(modelFactory, "modelFactory");
        this.tools = Objects.requireNonNull(tools, "tools");
        this.budget = Objects.requireNonNull(budget, "budget");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 创建每次运行都获得独立模型会话的运行时。
     *
     * @param modelFactory 模型会话工厂
     * @param tools 工具注册表
     * @param maxSteps 最大模型调用次数
     * @return 模型状态按运行隔离的运行时
     */
    public static AgentRuntime withModelFactory(
            Supplier<? extends AgentModelPort> modelFactory,
            ToolRegistry tools,
            int maxSteps) {
        return withModelFactory(modelFactory, tools, RunBudget.fromMaxSteps(maxSteps), NanoClock.system());
    }

    /**
     * 创建带完整预算、且每次运行获得独立模型会话的运行时。
     *
     * @param modelFactory 模型会话工厂
     * @param tools 工具注册表
     * @param budget 单次运行预算
     * @return 模型状态按运行隔离的运行时
     */
    public static AgentRuntime withModelFactory(
            Supplier<? extends AgentModelPort> modelFactory,
            ToolRegistry tools,
            RunBudget budget) {
        return withModelFactory(modelFactory, tools, budget, NanoClock.system());
    }

    /**
     * 创建注入单调时钟的运行时，供确定性截止时间测试或平台时钟适配使用。
     *
     * @param modelFactory 模型会话工厂
     * @param tools 工具注册表
     * @param budget 单次运行预算
     * @param clock 单调时钟
     * @return 配置完成的运行时
     */
    public static AgentRuntime withModelFactory(
            Supplier<? extends AgentModelPort> modelFactory,
            ToolRegistry tools,
            RunBudget budget,
            NanoClock clock) {
        return new AgentRuntime(modelFactory, tools, budget, clock);
    }

    private static Supplier<AgentModelPort> singletonFactory(AgentModelPort model) {
        AgentModelPort snapshot = Objects.requireNonNull(model, "model");
        return () -> snapshot;
    }

    /**
     * 执行一次 Agent 运行，直到完成、失败或命中明确预算边界。
     *
     * @param input 非空白用户输入
     * @return 包含不可变轨迹和消耗快照的终态结果
     */
    public Result run(String input) {
        Arguments.nonBlank(input, "input");
        AgentModelPort model = Objects.requireNonNull(
                modelFactory.get(), "modelFactory returned null");
        RunTracker tracker = new RunTracker(clock.nanoTime());

        while (true) {
            Stopped preModelStop = tracker.preModelStop();
            if (preModelStop != null) {
                return preModelStop;
            }

            tracker.trace.add(AgentState.MODEL);
            tracker.modelCalls++;
            AgentDecision decision;
            try {
                decision = Objects.requireNonNull(
                        model.decide(new AgentContext(input, tracker.history, tools.definitions())),
                        "model returned null decision");
            } catch (ModelExecutionException failure) {
                tracker.trace.add(AgentState.STOP);
                return new ModelFailed(failure, tracker.trace, tracker.history,
                        tracker.steps, tracker.usage());
            }

            tracker.steps.add(new AgentStep(tracker.modelCalls, decision, Optional.empty()));
            if (decision instanceof AgentDecision.FinalAnswer answer) {
                tracker.trace.add(AgentState.FINAL);
                tracker.trace.add(AgentState.STOP);
                return new Completed(answer, tracker.trace, tracker.history,
                        tracker.steps, tracker.usage());
            }

            AgentDecision.ToolCall call = (AgentDecision.ToolCall) decision;
            tracker.trace.add(AgentState.TOOL);
            Stopped preToolStop = tracker.preToolStop(call);
            if (preToolStop != null) {
                return preToolStop;
            }

            ToolResult result;
            tracker.toolCalls++;
            try {
                result = tools.execute(call);
            } catch (ToolRegistry.RejectedCall rejected) {
                return tracker.rejected(rejected);
            } catch (ToolExecutionException failure) {
                tracker.trace.add(AgentState.STOP);
                return new ToolFailed(call, failure, tracker.trace, tracker.history,
                        tracker.steps, tracker.usage());
            }
            tracker.executedCallIds.add(call.callId());
            tracker.history.add(new AgentContext.Exchange(call, result));
            tracker.steps.set(tracker.steps.size() - 1,
                    new AgentStep(tracker.modelCalls, decision, Optional.of(result)));
        }
    }

    /** 保存一次运行的可变状态，并集中创建不可变终态快照。 */
    private final class RunTracker {
        private final long startedAt;
        private final List<AgentState> trace = new ArrayList<>(List.of(AgentState.START));
        private final List<AgentContext.Exchange> history = new ArrayList<>();
        private final List<AgentStep> steps = new ArrayList<>();
        private final Set<String> executedCallIds = new HashSet<>();
        private int modelCalls;
        private int toolCalls;

        private RunTracker(long startedAt) {
            this.startedAt = startedAt;
        }

        private Stopped preModelStop() {
            if (deadlineReached()) {
                return stopped(StopReason.DEADLINE_EXCEEDED, "run deadline reached");
            }
            return null;
        }

        private Stopped preToolStop(AgentDecision.ToolCall call) {
            if (executedCallIds.contains(call.callId())) {
                return stopped(StopReason.DUPLICATE_CALL_ID,
                        "duplicate callId: " + call.callId());
            }
            if (deadlineReached()) {
                return stopped(StopReason.DEADLINE_EXCEEDED, "run deadline reached");
            }
            if (modelCalls >= budget.maxModelCalls()) {
                return stopped(StopReason.MODEL_CALL_LIMIT,
                        "model call limit reached before tool execution: " + budget.maxModelCalls());
            }
            if (toolCalls >= budget.maxToolCalls()) {
                return stopped(StopReason.TOOL_CALL_LIMIT,
                        "tool call limit reached: " + budget.maxToolCalls());
            }
            return null;
        }

        private Stopped rejected(ToolRegistry.RejectedCall rejected) {
            return stopped(mapRejection(rejected.reason()), rejected.getMessage());
        }

        private boolean deadlineReached() {
            return budget.hasDeadline() && elapsed().compareTo(budget.maxDuration()) >= 0;
        }

        private Duration elapsed() {
            return Duration.ofNanos(clock.nanoTime() - startedAt);
        }

        private RunUsage usage() {
            return new RunUsage(modelCalls, toolCalls, elapsed());
        }

        private Stopped stopped(StopReason reason, String detail) {
            trace.add(AgentState.STOP);
            return new Stopped(reason, detail, trace, history, steps, usage());
        }
    }

    static StopReason mapRejection(ToolRegistry.Rejection rejection) {
        return switch (rejection) {
            case UNKNOWN_TOOL -> StopReason.UNKNOWN_TOOL;
            case INVALID_ARGUMENTS -> StopReason.INVALID_ARGUMENTS;
            case DUPLICATE_CALL_ID -> StopReason.DUPLICATE_CALL_ID;
        };
    }

    /** 一次运行的供应商中立终态。 */
    public sealed interface Result permits Completed, Stopped, ToolFailed, ModelFailed {
        /**
         * 返回不可修改的生命周期轨迹。
         *
         * @return 生命周期轨迹
         */
        List<AgentState> trace();

        /**
         * 返回不可修改的成功工具交互历史。
         *
         * @return 成功工具交互历史
         */
        List<AgentContext.Exchange> history();

        /**
         * 返回不可修改的模型决策步骤。
         *
         * @return 模型决策步骤
         */
        List<AgentStep> steps();

        /**
         * 返回终态资源消耗快照。
         *
         * @return 资源消耗快照
         */
        RunUsage usage();

        /**
         * 返回已产生的模型决策步骤数。
         *
         * @return 模型决策步骤数
         */
        default int stepsTaken() {
            return steps().size();
        }
    }

    /**
     * 模型成功产生最终回答的结果。
     *
     * @param answer 最终回答
     * @param trace 不可变生命周期轨迹来源
     * @param history 不可变成功工具历史来源
     * @param steps 不可变模型步骤来源
     * @param usage 终态资源消耗快照
     */
    public record Completed(AgentDecision.FinalAnswer answer, List<AgentState> trace,
                            List<AgentContext.Exchange> history, List<AgentStep> steps,
                            RunUsage usage) implements Result {
        /** 创建完成结果并复制集合快照。 */
        public Completed {
            Objects.requireNonNull(answer, "answer");
            trace = List.copyOf(trace);
            history = List.copyOf(history);
            steps = List.copyOf(steps);
            Objects.requireNonNull(usage, "usage");
        }
    }

    /**
     * 因安全边界或预算耗尽而停止的结果。
     *
     * @param reason 稳定停止分类
     * @param detail 不包含秘密的停止说明
     * @param trace 不可变生命周期轨迹来源
     * @param history 不可变成功工具历史来源
     * @param steps 不可变模型步骤来源
     * @param usage 终态资源消耗快照
     */
    public record Stopped(StopReason reason, String detail, List<AgentState> trace,
                          List<AgentContext.Exchange> history, List<AgentStep> steps,
                          RunUsage usage) implements Result {
        /** 创建停止结果并复制集合快照。 */
        public Stopped {
            Objects.requireNonNull(reason, "reason");
            Arguments.nonBlank(detail, "stop detail");
            trace = List.copyOf(trace);
            history = List.copyOf(history);
            steps = List.copyOf(steps);
            Objects.requireNonNull(usage, "usage");
        }
    }

    /**
     * 工具适配器明确归类的可预期失败结果。
     *
     * @param call 失败的工具调用
     * @param failure 已归类工具失败
     * @param trace 不可变生命周期轨迹来源
     * @param history 不可变成功工具历史来源
     * @param steps 不可变模型步骤来源
     * @param usage 终态资源消耗快照
     */
    public record ToolFailed(AgentDecision.ToolCall call, ToolExecutionException failure,
                             List<AgentState> trace, List<AgentContext.Exchange> history,
                             List<AgentStep> steps, RunUsage usage) implements Result {
        /** 创建工具失败结果并复制集合快照。 */
        public ToolFailed {
            Objects.requireNonNull(call, "call");
            Objects.requireNonNull(failure, "failure");
            trace = List.copyOf(trace);
            history = List.copyOf(history);
            steps = List.copyOf(steps);
            Objects.requireNonNull(usage, "usage");
        }
    }

    /**
     * 模型适配器明确归类的可预期失败结果。
     *
     * @param failure 已归类模型失败
     * @param trace 不可变生命周期轨迹来源
     * @param history 不可变成功工具历史来源
     * @param steps 不可变模型步骤来源
     * @param usage 终态资源消耗快照
     */
    public record ModelFailed(ModelExecutionException failure, List<AgentState> trace,
                              List<AgentContext.Exchange> history, List<AgentStep> steps,
                              RunUsage usage) implements Result {
        /** 创建模型失败结果并复制集合快照。 */
        public ModelFailed {
            Objects.requireNonNull(failure, "failure");
            trace = List.copyOf(trace);
            history = List.copyOf(history);
            steps = List.copyOf(steps);
            Objects.requireNonNull(usage, "usage");
        }
    }

    /** 运行时主动停止的稳定分类。 */
    public enum StopReason {
        /** 模型请求了未注册工具。 */
        UNKNOWN_TOOL,
        /** 工具参数违反本地契约。 */
        INVALID_ARGUMENTS,
        /** 同一次运行重复出现已执行的 callId。 */
        DUPLICATE_CALL_ID,
        /** 已耗尽模型调用预算。 */
        MODEL_CALL_LIMIT,
        /** 已耗尽应用工具调用预算。 */
        TOOL_CALL_LIMIT,
        /** 已到达运行截止时间。 */
        DEADLINE_EXCEEDED
    }
}
