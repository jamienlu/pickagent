package io.github.jamielu.agent.integration;

import io.github.jamielu.agent.api.AgentDecision;
import io.github.jamielu.agent.api.AgentStep;
import io.github.jamielu.agent.api.ToolDefinition;
import io.github.jamielu.agent.api.ToolResult;
import io.github.jamielu.agent.internal.Arguments;
import io.github.jamielu.agent.openai.OpenAiResponseOptions;
import io.github.jamielu.agent.reliability.IdempotencyStore;
import io.github.jamielu.agent.reliability.RetryDecision;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ContractValidationBranchesTest {
    // 场景：基础非空白校验收到空引用和空白值；行为：调用共享校验器；预期：两种输入均被拒绝。
    @Test
    void sharedStringValidationRejectsNullAndBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> Arguments.nonBlank(null, "field"));
        assertThrows(IllegalArgumentException.class,
                () -> Arguments.nonBlank(" ", "field"));
    }

    // 场景：步骤编号或观察类型非法；行为：构造不可变步骤；预期：拒绝非正编号和最终回答携带工具观察。
    @Test
    void agentStepRejectsInvalidNumberAndNonToolObservation() {
        assertThrows(IllegalArgumentException.class,
                () -> new AgentStep(0, new AgentDecision.FinalAnswer("done"), Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new AgentStep(1, new AgentDecision.FinalAnswer("done"),
                        Optional.of(new ToolResult("call-1", "output"))));
    }

    // 场景：工具名称不符合核心命名规则；行为：构造工具定义；预期：在公开给模型前被拒绝。
    @Test
    void toolDefinitionRejectsInvalidNamePattern() {
        assertThrows(IllegalArgumentException.class,
                () -> new ToolDefinition("Bad-Name", "description", List.of()));
    }

    // 场景：重试决策值对象收到空或非法值；行为：构造决策；预期：完整保护延迟和原因不变量。
    @Test
    void retryDecisionRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new RetryDecision.RetryAfter(null));
        assertThrows(IllegalArgumentException.class,
                () -> new RetryDecision.RetryAfter(Duration.ofNanos(-1)));
        assertThrows(IllegalArgumentException.class,
                () -> new RetryDecision.Stop(null));
        assertThrows(IllegalArgumentException.class,
                () -> new RetryDecision.Stop(" "));
    }

    // 场景：幂等缓存条目缺少请求指纹；行为：构造条目；预期：空引用和空白指纹都被拒绝。
    @Test
    void idempotencyEntryRejectsInvalidFingerprint() {
        assertThrows(IllegalArgumentException.class,
                () -> new IdempotencyStore.Entry<>(null, "result"));
        assertThrows(IllegalArgumentException.class,
                () -> new IdempotencyStore.Entry<>(" ", "result"));
    }

    // 场景：Responses 请求输出上限非法；行为：构造请求选项；预期：拒绝零和负数。
    @Test
    void responseOptionsRejectNonPositiveOutputLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> new OpenAiResponseOptions(0, true));
        assertThrows(IllegalArgumentException.class,
                () -> new OpenAiResponseOptions(-1, false));
    }
}
