package io.github.jamielu.agent.reliability;

import java.time.Duration;

/** 封闭的重试决策：等待后重试或携带原因停止。 */
public sealed interface RetryDecision permits RetryDecision.RetryAfter, RetryDecision.Stop {
    /**
     * 至少等待指定时长后重试；该类型本身不执行等待。
     *
     * @param delay 重试前的非负等待时长
     */
    record RetryAfter(Duration delay) implements RetryDecision {
        /** 校验重试等待时长。 */
        public RetryAfter {
            if (delay == null || delay.isNegative()) {
                throw new IllegalArgumentException("delay must be non-negative");
            }
        }
    }

    /**
     * 使用给定诊断原因停止重试。
     *
     * @param reason 非空白停止原因
     */
    record Stop(String reason) implements RetryDecision {
        /** 校验停止原因。 */
        public Stop {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reason cannot be null or blank");
            }
        }
    }
}


