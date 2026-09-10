package io.github.jamielu.agent.online;

import io.github.jamielu.agent.openai.OpenAiResponseOptions;
import io.github.jamielu.agent.runtime.RunBudget;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 从外部环境构建的在线运行配置；字符串表示不会输出 API 密钥。
 */
public final class OpenAiOnlineConfig {
    private final String apiKey;
    private final String model;
    private final Optional<String> instructions;
    private final Optional<String> baseUrl;
    private final Duration timeout;
    private final int maxRetries;
    private final OpenAiResponseOptions responseOptions;
    private final RunBudget runBudget;

    /**
     * 创建完整在线配置。
     *
     * @param apiKey 非空白 API 密钥
     * @param model 非空白模型标识
     * @param instructions 可选指令
     * @param baseUrl 可选兼容端点根地址
     * @param timeout 单次 SDK 请求超时
     * @param maxRetries SDK 拥有的最大重试次数
     * @param responseOptions 单响应输出与存储选项
     * @param runBudget 跨轮运行预算
     */
    public OpenAiOnlineConfig(
            String apiKey,
            String model,
            Optional<String> instructions,
            Optional<String> baseUrl,
            Duration timeout,
            int maxRetries,
            OpenAiResponseOptions responseOptions,
            RunBudget runBudget) {
        this.apiKey = requireNonBlank(apiKey, "OPENAI_API_KEY");
        this.model = requireNonBlank(model, "OPENAI_MODEL");
        this.instructions = normalizeOptional(instructions, "OPENAI_INSTRUCTIONS");
        this.baseUrl = normalizeOptional(baseUrl, "OPENAI_BASE_URL");
        this.timeout = requirePositive(timeout, "OPENAI_TIMEOUT_SECONDS");
        if (maxRetries < 0) {
            throw new IllegalArgumentException("OPENAI_MAX_RETRIES must not be negative");
        }
        this.maxRetries = maxRetries;
        this.responseOptions = Objects.requireNonNull(responseOptions, "responseOptions");
        this.runBudget = Objects.requireNonNull(runBudget, "runBudget");
    }

    /**
     * 从给定环境变量快照读取配置。
     *
     * @param environment 环境变量快照
     * @return 校验完成的在线配置
     */
    public static OpenAiOnlineConfig fromEnvironment(Map<String, String> environment) {
        Map<String, String> env = Map.copyOf(Objects.requireNonNull(environment, "environment"));
        return new OpenAiOnlineConfig(
                env.get("OPENAI_API_KEY"),
                env.get("OPENAI_MODEL"),
                optional(env.get("OPENAI_INSTRUCTIONS")),
                optional(env.get("OPENAI_BASE_URL")),
                Duration.ofSeconds(positiveLong(env, "OPENAI_TIMEOUT_SECONDS", 30)),
                nonNegativeInt(env, "OPENAI_MAX_RETRIES", 2),
                new OpenAiResponseOptions(
                        positiveLong(env, "OPENAI_MAX_OUTPUT_TOKENS", 1024),
                        booleanValue(env, "OPENAI_STORE", true)),
                new RunBudget(
                        positiveInt(env, "AGENT_MAX_MODEL_CALLS", 8),
                        nonNegativeInt(env, "AGENT_MAX_TOOL_CALLS", 4),
                        Duration.ofSeconds(positiveLong(env, "AGENT_MAX_RUN_SECONDS", 120))));
    }

    /**
     * 返回 API 密钥；调用方不得记录或输出该值。
     *
     * @return API 密钥
     */
    public String apiKey() {
        return apiKey;
    }

    /**
     * 返回模型标识。
     *
     * @return 模型标识
     */
    public String model() {
        return model;
    }

    /**
     * 返回可选请求指令。
     *
     * @return 可选请求指令
     */
    public Optional<String> instructions() {
        return instructions;
    }

    /**
     * 返回可选端点根地址。
     *
     * @return 可选端点根地址
     */
    public Optional<String> baseUrl() {
        return baseUrl;
    }

    /**
     * 返回 SDK 请求超时。
     *
     * @return SDK 请求超时
     */
    public Duration timeout() {
        return timeout;
    }

    /**
     * 返回 SDK 拥有的最大重试次数。
     *
     * @return SDK 最大重试次数
     */
    public int maxRetries() {
        return maxRetries;
    }

    /**
     * 返回单响应请求选项。
     *
     * @return 单响应请求选项
     */
    public OpenAiResponseOptions responseOptions() {
        return responseOptions;
    }

    /**
     * 返回跨轮运行预算。
     *
     * @return 跨轮运行预算
     */
    public RunBudget runBudget() {
        return runBudget;
    }

    /** 返回不包含密钥的诊断字符串。 */
    @Override
    public String toString() {
        return "OpenAiOnlineConfig[apiKey=***, model=" + model
                + ", instructions=" + instructions.map(ignored -> "***")
                + ", baseUrl=" + baseUrl + ", timeout=" + timeout
                + ", maxRetries=" + maxRetries + ", responseOptions=" + responseOptions
                + ", runBudget=" + runBudget + "]";
    }

    private static Optional<String> optional(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    private static Optional<String> normalizeOptional(Optional<String> value, String name) {
        return Objects.requireNonNull(value, name)
                .map(item -> requireNonBlank(item, name).trim());
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be configured");
        }
        return value.trim();
    }

    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static int positiveInt(Map<String, String> env, String name, int defaultValue) {
        int value = integer(env, name, defaultValue);
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static int nonNegativeInt(Map<String, String> env, String name, int defaultValue) {
        int value = integer(env, name, defaultValue);
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    private static int integer(Map<String, String> env, String name, int defaultValue) {
        String raw = env.get(name);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(name + " must be an integer", failure);
        }
    }

    private static long positiveLong(Map<String, String> env, String name, long defaultValue) {
        String raw = env.get(name);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            long value = Long.parseLong(raw.trim());
            if (value < 1) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return value;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(name + " must be an integer", failure);
        }
    }

    private static boolean booleanValue(
            Map<String, String> env, String name, boolean defaultValue) {
        String raw = env.get(name);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        if (raw.equalsIgnoreCase("true")) {
            return true;
        }
        if (raw.equalsIgnoreCase("false")) {
            return false;
        }
        throw new IllegalArgumentException(name + " must be true or false");
    }
}
