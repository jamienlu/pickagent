package io.github.jamielu.agent.runtime;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RunUsageTest {
    // 场景：构造有效消耗快照；行为：读取计数和耗时；预期：所有值保持不变。
    @Test
    void acceptsValidUsage() {
        RunUsage usage = new RunUsage(2, 1, Duration.ofMillis(5));

        assertEquals(2, usage.modelCalls());
        assertEquals(1, usage.toolCalls());
        assertEquals(Duration.ofMillis(5), usage.elapsed());
    }

    // 场景：传入非法消耗字段；行为：构造快照；预期：拒绝负计数、负耗时和空耗时。
    @Test
    void rejectsInvalidUsageFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new RunUsage(-1, 0, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new RunUsage(0, -1, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new RunUsage(0, 0, Duration.ofNanos(-1)));
        assertThrows(NullPointerException.class,
                () -> new RunUsage(0, 0, null));
    }
}
