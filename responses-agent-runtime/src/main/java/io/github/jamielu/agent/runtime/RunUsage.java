package io.github.jamielu.agent.runtime;

import java.time.Duration;
import java.util.Objects;

/**
 * 一次运行在某个终态时的不可变资源消耗快照。
 *
 * @param modelCalls 已发起的模型调用次数
 * @param toolCalls 已发起的应用工具调用次数
 * @param elapsed 从运行开始计算的单调时长
 */
public record RunUsage(int modelCalls, int toolCalls, Duration elapsed) {
    /** 创建并校验消耗快照。 */
    public RunUsage {
        if (modelCalls < 0 || toolCalls < 0) {
            throw new IllegalArgumentException("usage counters must not be negative");
        }
        Objects.requireNonNull(elapsed, "elapsed");
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("elapsed must not be negative");
        }
    }
}
