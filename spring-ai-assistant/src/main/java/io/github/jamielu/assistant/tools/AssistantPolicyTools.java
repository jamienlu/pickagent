package io.github.jamielu.assistant.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** 提供确定性、只读且没有外部副作用的助手策略查询工具。 */
public final class AssistantPolicyTools {
    /** 暴露给模型的稳定工具名称。 */
    public static final String TOOL_NAME = "lookup_policy";

    /** 工具允许查询的完整主题白名单。 */
    public static final List<String> SUPPORTED_TOPICS = List.of(
            "stream-timeout",
            "max-output-tokens",
            "privacy");

    private final Map<String, String> policies;

    /**
     * 根据已校验的应用配置创建不可变策略快照。
     *
     * @param signalTimeout 首个及相邻文本信号的最大等待时间
     * @param maxOutputTokens 单次响应的最大输出 Token 数
     */
    public AssistantPolicyTools(Duration signalTimeout, int maxOutputTokens) {
        Objects.requireNonNull(signalTimeout, "signalTimeout");
        if (signalTimeout.isZero() || signalTimeout.isNegative()) {
            throw new IllegalArgumentException("signalTimeout must be greater than zero");
        }
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be greater than zero");
        }
        this.policies = Map.of(
                "stream-timeout",
                "stream-timeout: maximum wait before the first text signal and between adjacent text signals is "
                        + signalTimeout + ".",
                "max-output-tokens",
                "max-output-tokens: synchronous and streaming prompts use maxTokens="
                        + maxOutputTokens + ".",
                "privacy",
                "privacy: this policy lookup is local and read-only; it performs no network access, "
                        + "file writes, database access, or other external side effects.");
    }

    /**
     * 查询一个白名单内的应用策略并返回稳定文本。
     *
     * @param topic 策略主题
     * @return 对应主题的确定性策略说明
     * @throws IllegalArgumentException 主题为空白或不在白名单内
     */
    @Tool(
            name = TOOL_NAME,
            description = "Look up one configured, read-only assistant policy by its supported topic.")
    public String lookupPolicy(
            @ToolParam(
                    description = "Policy topic: stream-timeout, max-output-tokens, or privacy.",
                    required = true)
            String topic) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("policy topic must not be blank");
        }
        String normalizedTopic = topic.trim().toLowerCase(Locale.ROOT);
        String result = this.policies.get(normalizedTopic);
        if (result == null) {
            throw new IllegalArgumentException(
                    "unsupported policy topic '" + normalizedTopic + "'; supported topics: "
                            + String.join(", ", SUPPORTED_TOPICS));
        }
        return result;
    }
}
