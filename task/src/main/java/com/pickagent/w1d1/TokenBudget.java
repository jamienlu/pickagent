package com.pickagent.w1d1;

/**
 * 计算一次模型请求在上下文窗口内的 Token 预算。
 *
 * <p>预算由上下文上限、输入 Token 和预留输出 Token 构成；对象创建后保持不可变。</p>
 *
 * @author jamieLu
 * @since 2026-08-24
 */
public class TokenBudget {
    /** 上下文窗口允许的最大 Token 数。 */
    private final long contextLimit;
    /** 本次请求已经占用的输入 Token 数。 */
    private final long inputTokens;
    /** 为模型输出预留的 Token 数。 */
    private final long reservedOutputTokens;

    /**
     * 获取上下文窗口上限。
     *
     * @return 上下文窗口允许的最大 Token 数
     */
    public long getContextLimit() {
        return contextLimit;
    }

    /**
     * 创建不可变的 Token 预算对象。
     *
     * @param contextLimit 上下文窗口上限
     * @param inputTokens 已使用的输入 Token 数
     * @param reservedOutputTokens 为输出预留的 Token 数
     * @throws IllegalArgumentException 任一参数为负数，或输入与预留输出相加发生溢出时抛出
     */
    public TokenBudget(long contextLimit, long inputTokens, long reservedOutputTokens) {
        if (contextLimit < 0 || inputTokens < 0 || reservedOutputTokens < 0) {
            throw new IllegalArgumentException("Context limit, input tokens, and reserved output tokens must be non-negative.");
        }
        if (Long.MAX_VALUE - inputTokens < reservedOutputTokens) {
            throw new IllegalArgumentException("The sum of input tokens and reserved output tokens cannot exceed the context limit.");
        }
        this.contextLimit = contextLimit;
        this.inputTokens = inputTokens;
        this.reservedOutputTokens = reservedOutputTokens;
    }

    /**
     * 判断输入与预留输出是否仍在上下文窗口内。
     *
     * @return 未超过上下文上限时返回 {@code true}
     */
    public boolean isWithinBudget() {
        return (inputTokens + reservedOutputTokens) <= contextLimit;
    }

    /**
     * 计算扣除输入和预留输出后的剩余 Token 数。
     *
     * @return 非负的剩余 Token 数
     * @throws IllegalStateException 当前预算已经超过上下文窗口时抛出
     */
    public long remainingBudget() {
        long res = contextLimit - (inputTokens + reservedOutputTokens);
        if (res < 0) {
            throw new IllegalStateException("Remaining budget is negative, which indicates an error in the token budget calculation.");
        }
        return res;
    }
}
