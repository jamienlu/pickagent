package io.github.jamielu.agent.runtime;

import java.time.Duration;
import java.util.Objects;

/**
 * 一次 Agent 运行的供应商中立资源上限。
 *
 * @param maxModelCalls 最大模型调用次数，必须为正数
 * @param maxToolCalls 最大应用工具调用次数，可以为零
 * @param maxDuration 最大运行时长；{@link Duration#ZERO} 表示不设置截止时间
 */
public record RunBudget(int maxModelCalls, int maxToolCalls, Duration maxDuration) {
    /** 创建并校验不可变预算。 */
    public RunBudget {
        if (maxModelCalls < 1) {
            throw new IllegalArgumentException("maxModelCalls must be positive");
        }
        if (maxToolCalls < 0) {
            throw new IllegalArgumentException("maxToolCalls must not be negative");
        }
        Objects.requireNonNull(maxDuration, "maxDuration");
        if (maxDuration.isNegative()) {
            throw new IllegalArgumentException("maxDuration must not be negative");
        }
    }

    /**
     * 将旧版最大步骤配置转换为等价预算。
     *
     * @param maxSteps 最大模型步骤数
     * @return 不设截止时间、且不会执行无人消费结果的预算
     */
    public static RunBudget fromMaxSteps(int maxSteps) {
        return new RunBudget(maxSteps, Math.max(0, maxSteps - 1), Duration.ZERO);
    }

    /**
     * 判断是否启用了运行截止时间。
     *
     * @return 最大时长非零时为 {@code true}
     */
    public boolean hasDeadline() {
        return !maxDuration.isZero();
    }
}
