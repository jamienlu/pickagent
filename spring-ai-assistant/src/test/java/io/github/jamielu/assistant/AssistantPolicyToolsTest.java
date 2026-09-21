package io.github.jamielu.assistant;

import io.github.jamielu.assistant.tools.AssistantPolicyTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.support.ToolCallbacks;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证只读策略工具的白名单、稳定元数据和输入失败边界。 */
class AssistantPolicyToolsTest {

    /** 验证三个白名单主题都返回稳定且与配置一致的领域结果。 */
    @ParameterizedTest(name = "主题 {0} 返回对应策略")
    @CsvSource({
            "stream-timeout, 'stream-timeout: maximum wait before the first text signal and between adjacent text signals is PT30S.'",
            "max-output-tokens, 'max-output-tokens: synchronous and streaming prompts use maxTokens=1024.'",
            "privacy, 'privacy: this policy lookup is local and read-only; it performs no network access, file writes, database access, or other external side effects.'"
    })
    void supportedTopicReturnsStablePolicy(String topic, String expectedResult) {
        var tools = new AssistantPolicyTools(Duration.ofSeconds(30), 1024);

        assertThat(tools.lookupPolicy(topic)).isEqualTo(expectedResult);
    }

    /** 验证未知主题被显式拒绝，并列出完整稳定白名单。 */
    @Test
    void unknownTopicIsRejectedWithSupportedTopics() {
        var tools = new AssistantPolicyTools(Duration.ofSeconds(30), 1024);

        assertThatThrownBy(() -> tools.lookupPolicy("billing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported policy topic 'billing'; supported topics: "
                        + "stream-timeout, max-output-tokens, privacy");
    }

    /** 验证空白参数在查询映射前立即失败。 */
    @Test
    void blankTopicIsRejectedBeforeLookup() {
        var tools = new AssistantPolicyTools(Duration.ofSeconds(30), 1024);

        assertThatThrownBy(() -> tools.lookupPolicy("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("policy topic must not be blank");
    }

    /** 验证声明式工具只暴露一个稳定名称，并将 topic 标记为必填字符串。 */
    @Test
    void toolContractHasOneStableNameAndRequiredTopic() {
        var callbacks = ToolCallbacks.from(new AssistantPolicyTools(Duration.ofSeconds(30), 1024));

        assertThat(callbacks).hasSize(1);
        assertThat(callbacks[0].getToolDefinition().name()).isEqualTo(AssistantPolicyTools.TOOL_NAME);
        assertThat(callbacks[0].getToolDefinition().description())
                .isEqualTo("Look up one configured, read-only assistant policy by its supported topic.");
        assertThat(callbacks[0].getToolDefinition().inputSchema())
                .contains("\"topic\"")
                .contains("\"type\" : \"string\"")
                .contains("\"required\" : [ \"topic\" ]");
    }
}
