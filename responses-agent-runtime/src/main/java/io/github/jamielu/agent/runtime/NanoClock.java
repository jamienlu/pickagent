package io.github.jamielu.agent.runtime;

/**
 * 提供可替换的单调纳秒时钟，以便离线、确定性地验证运行截止时间。
 */
@FunctionalInterface
public interface NanoClock {
    /**
     * 返回单调递增的时间读数。
     *
     * @return 纳秒读数，仅用于计算时间差
     */
    long nanoTime();

    /**
     * 返回 JVM 提供的单调时钟。
     *
     * @return 系统单调时钟
     */
    static NanoClock system() {
        return System::nanoTime;
    }
}
