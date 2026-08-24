package com.pickagent.w1d1;

/**
 * @author jamieLu
 * @create 2026-08-24
 */

public class TokenBudget {
    private final long contextLimit;
    private final long inputTokens;
    private final long reservedOutputTokens;

    public long getContextLimit() {
        return contextLimit;
    }

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
    public boolean isWithinBudget() {
        return (inputTokens + reservedOutputTokens) <= contextLimit;
    }

    public long remainingBudget() {
        long res = contextLimit - (inputTokens + reservedOutputTokens);
        if (res < 0) {
            throw new IllegalStateException("Remaining budget is negative, which indicates an error in the token budget calculation.");
        }
        return  res;
    }
}
