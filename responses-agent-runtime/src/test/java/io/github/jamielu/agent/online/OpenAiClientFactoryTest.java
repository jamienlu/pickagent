package io.github.jamielu.agent.online;

import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiClientFactoryTest {
    // 场景：使用默认官方端点配置；行为：创建并关闭 SDK 客户端；预期：不发网络请求即可完成资源生命周期。
    @Test
    void createsClientWithDefaultEndpoint() {
        OpenAIClient client = new OpenAiClientFactory().create(config(Map.of()));
        try {
            assertNotNull(client);
        } finally {
            client.close();
        }
    }

    // 场景：配置兼容端点；行为：创建并关闭 SDK 客户端；预期：端点分支可离线完成装配。
    @Test
    void createsClientWithCustomEndpoint() {
        OpenAIClient client = new OpenAiClientFactory().create(config(Map.of(
                "OPENAI_BASE_URL", "https://example.test/v1")));
        try {
            assertNotNull(client);
        } finally {
            client.close();
        }
    }

    // 场景：客户端工厂收到空配置；行为：创建客户端；预期：立即拒绝而非依赖 SDK 报错。
    @Test
    void rejectsNullConfig() {
        assertThrows(NullPointerException.class,
                () -> new OpenAiClientFactory().create(null));
    }

    private static OpenAiOnlineConfig config(Map<String, String> overrides) {
        Map<String, String> env = new HashMap<>(Map.of(
                "OPENAI_API_KEY", "offline-placeholder",
                "OPENAI_MODEL", "gpt-test",
                "OPENAI_MAX_RETRIES", "0"));
        env.putAll(overrides);
        return OpenAiOnlineConfig.fromEnvironment(env);
    }
}
