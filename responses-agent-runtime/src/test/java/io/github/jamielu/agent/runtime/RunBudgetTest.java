package io.github.jamielu.agent.runtime;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunBudgetTest {
    // 场景：构造有效预算；行为：读取三个上限；预期：数值保持不变且正时长启用截止时间。
    @Test
    void acceptsValidBudget() {
        RunBudget budget = new RunBudget(3, 2, Duration.ofSeconds(10));

        assertEquals(3, budget.maxModelCalls());
        assertEquals(2, budget.maxToolCalls());
        assertEquals(Duration.ofSeconds(10), budget.maxDuration());
        assertTrue(budget.hasDeadline());
    }

    // 场景：使用旧版步骤配置；行为：转换为新预算；预期：保留模型上限、预留续接并关闭截止时间。
    @Test
    void convertsLegacyStepLimit() {
        RunBudget budget = RunBudget.fromMaxSteps(1);

        assertEquals(1, budget.maxModelCalls());
        assertEquals(0, budget.maxToolCalls());
        assertFalse(budget.hasDeadline());
    }

    // 场景：传入非法预算字段；行为：构造预算；预期：逐项拒绝非正模型数、负工具数、负时长和空时长。
    @Test
    void rejectsInvalidBudgetFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new RunBudget(0, 0, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new RunBudget(1, -1, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new RunBudget(1, 0, Duration.ofNanos(-1)));
        assertThrows(NullPointerException.class,
                () -> new RunBudget(1, 0, null));
    }
}
