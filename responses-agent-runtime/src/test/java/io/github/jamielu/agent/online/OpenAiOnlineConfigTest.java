package io.github.jamielu.agent.online;

import io.github.jamielu.agent.openai.OpenAiResponseOptions;
import io.github.jamielu.agent.runtime.RunBudget;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiOnlineConfigTest {
    // 场景：只提供必需环境变量；行为：读取在线配置；预期：安全默认值完整生效。
    @Test
    void readsRequiredValuesAndSafeDefaults() {
        OpenAiOnlineConfig config = OpenAiOnlineConfig.fromEnvironment(
                Map.of("OPENAI_API_KEY", "secret-value", "OPENAI_MODEL", "gpt-test"));

        assertEquals("secret-value", config.apiKey());
        assertEquals("gpt-test", config.model());
        assertTrue(config.instructions().isEmpty());
        assertTrue(config.baseUrl().isEmpty());
        assertEquals(Duration.ofSeconds(30), config.timeout());
        assertEquals(2, config.maxRetries());
        assertEquals(new OpenAiResponseOptions(1024, true), config.responseOptions());
        assertEquals(new RunBudget(8, 4, Duration.ofSeconds(120)), config.runBudget());
        assertFalse(config.toString().contains("secret-value"));
    }

    // 场景：提供全部外部配置；行为：解析空白、数字和布尔值；预期：裁剪并映射到请求与运行预算。
    @Test
    void readsAllOverrides() {
        Map<String, String> env = new HashMap<>();
        env.put("OPENAI_API_KEY", " key ");
        env.put("OPENAI_MODEL", " model ");
        env.put("OPENAI_INSTRUCTIONS", " instructions ");
        env.put("OPENAI_BASE_URL", " https://example.test/v1 ");
        env.put("OPENAI_TIMEOUT_SECONDS", "15");
        env.put("OPENAI_MAX_RETRIES", "0");
        env.put("OPENAI_MAX_OUTPUT_TOKENS", "256");
        env.put("OPENAI_STORE", "false");
        env.put("AGENT_MAX_MODEL_CALLS", "3");
        env.put("AGENT_MAX_TOOL_CALLS", "0");
        env.put("AGENT_MAX_RUN_SECONDS", "9");

        OpenAiOnlineConfig config = OpenAiOnlineConfig.fromEnvironment(env);

        assertEquals("key", config.apiKey());
        assertEquals("model", config.model());
        assertEquals(Optional.of("instructions"), config.instructions());
        assertEquals(Optional.of("https://example.test/v1"), config.baseUrl());
        assertEquals(Duration.ofSeconds(15), config.timeout());
        assertEquals(0, config.maxRetries());
        assertEquals(new OpenAiResponseOptions(256, false), config.responseOptions());
        assertEquals(new RunBudget(3, 0, Duration.ofSeconds(9)), config.runBudget());
        assertTrue(config.toString().contains("apiKey=***"));
    }

    // 场景：可选环境变量为空白；行为：读取配置；预期：按缺省值处理且不制造空字符串选项。
    @Test
    void treatsBlankOptionalAndDefaultableValuesAsAbsent() {
        Map<String, String> env = new HashMap<>();
        env.put("OPENAI_API_KEY", "key");
        env.put("OPENAI_MODEL", "model");
        env.put("OPENAI_INSTRUCTIONS", " ");
        env.put("OPENAI_BASE_URL", " ");
        env.put("OPENAI_TIMEOUT_SECONDS", " ");
        env.put("OPENAI_MAX_RETRIES", " ");
        env.put("OPENAI_MAX_OUTPUT_TOKENS", " ");
        env.put("OPENAI_STORE", " ");

        OpenAiOnlineConfig config = OpenAiOnlineConfig.fromEnvironment(env);

        assertTrue(config.instructions().isEmpty());
        assertTrue(config.baseUrl().isEmpty());
        assertEquals(Duration.ofSeconds(30), config.timeout());
        assertTrue(config.responseOptions().store());
    }

    // 场景：缺少 API 密钥或模型；行为：读取配置；预期：快速失败且错误信息只指出变量名。
    @Test
    void rejectsMissingRequiredEnvironment() {
        assertThrows(IllegalArgumentException.class,
                () -> OpenAiOnlineConfig.fromEnvironment(Map.of("OPENAI_MODEL", "model")));
        assertThrows(IllegalArgumentException.class,
                () -> OpenAiOnlineConfig.fromEnvironment(Map.of("OPENAI_API_KEY", "key")));
        assertThrows(IllegalArgumentException.class,
                () -> OpenAiOnlineConfig.fromEnvironment(Map.of(
                        "OPENAI_API_KEY", " ", "OPENAI_MODEL", "model")));
    }

    // 场景：数字环境变量格式或范围非法；行为：读取配置；预期：每一类非法值均被拒绝。
    @Test
    void rejectsInvalidNumericEnvironment() {
        assertInvalid("OPENAI_TIMEOUT_SECONDS", "0");
        assertInvalid("OPENAI_TIMEOUT_SECONDS", "abc");
        assertInvalid("OPENAI_MAX_RETRIES", "-1");
        assertInvalid("OPENAI_MAX_RETRIES", "abc");
        assertInvalid("OPENAI_MAX_OUTPUT_TOKENS", "0");
        assertInvalid("OPENAI_MAX_OUTPUT_TOKENS", "abc");
        assertInvalid("AGENT_MAX_MODEL_CALLS", "0");
        assertInvalid("AGENT_MAX_MODEL_CALLS", "abc");
        assertInvalid("AGENT_MAX_TOOL_CALLS", "-1");
        assertInvalid("AGENT_MAX_RUN_SECONDS", "0");
    }

    // 场景：存储开关不是标准布尔值；行为：读取配置；预期：拒绝含糊配置。
    @Test
    void rejectsInvalidBooleanEnvironment() {
        assertInvalid("OPENAI_STORE", "yes");
    }

    // 场景：直接构造配置时传入非法对象；行为：执行构造校验；预期：拒绝空选项、非正超时和负重试数。
    @Test
    void constructorProtectsInvariants() {
        OpenAiResponseOptions options = OpenAiResponseOptions.defaults();
        RunBudget budget = new RunBudget(1, 0, Duration.ZERO);
        assertThrows(NullPointerException.class, () -> new OpenAiOnlineConfig(
                "key", "model", null, Optional.empty(), Duration.ofSeconds(1), 0, options, budget));
        assertThrows(NullPointerException.class, () -> new OpenAiOnlineConfig(
                "key", "model", Optional.empty(), null, Duration.ofSeconds(1), 0, options, budget));
        assertThrows(IllegalArgumentException.class, () -> new OpenAiOnlineConfig(
                "key", "model", Optional.empty(), Optional.empty(), Duration.ZERO, 0, options, budget));
        assertThrows(IllegalArgumentException.class, () -> new OpenAiOnlineConfig(
                "key", "model", Optional.empty(), Optional.empty(),
                Duration.ofSeconds(-1), 0, options, budget));
        assertThrows(IllegalArgumentException.class, () -> new OpenAiOnlineConfig(
                "key", "model", Optional.empty(), Optional.empty(), Duration.ofSeconds(1), -1, options, budget));
        assertThrows(NullPointerException.class, () -> new OpenAiOnlineConfig(
                "key", "model", Optional.empty(), Optional.empty(), Duration.ofSeconds(1), 0, null, budget));
        assertThrows(NullPointerException.class, () -> new OpenAiOnlineConfig(
                "key", "model", Optional.empty(), Optional.empty(), Duration.ofSeconds(1), 0, options, null));
    }

    // 场景：显式配置大小写混合的 true；行为：解析存储开关；预期：得到启用状态。
    @Test
    void parsesExplicitTrueBoolean() {
        Map<String, String> env = new HashMap<>(Map.of(
                "OPENAI_API_KEY", "key", "OPENAI_MODEL", "model",
                "OPENAI_STORE", "TrUe"));

        assertTrue(OpenAiOnlineConfig.fromEnvironment(env).responseOptions().store());
    }

    private static void assertInvalid(String name, String value) {
        Map<String, String> env = new HashMap<>(Map.of(
                "OPENAI_API_KEY", "key", "OPENAI_MODEL", "model"));
        env.put(name, value);
        assertThrows(IllegalArgumentException.class,
                () -> OpenAiOnlineConfig.fromEnvironment(env));
    }
}
