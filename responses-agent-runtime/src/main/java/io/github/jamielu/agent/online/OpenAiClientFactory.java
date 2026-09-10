package io.github.jamielu.agent.online;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;

import java.util.Objects;

/** 创建拥有超时和唯一传输重试责任的生产 OpenAI SDK 客户端。 */
public final class OpenAiClientFactory {
    /** 创建无状态 SDK 客户端工厂。 */
    public OpenAiClientFactory() {
    }

    /**
     * 根据外部配置创建客户端；本方法不会发起网络请求。
     *
     * @param config 在线配置
     * @return 已配置客户端
     */
    public OpenAIClient create(OpenAiOnlineConfig config) {
        OpenAiOnlineConfig checked = Objects.requireNonNull(config, "config");
        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder()
                .apiKey(checked.apiKey())
                .timeout(checked.timeout())
                .maxRetries(checked.maxRetries());
        checked.baseUrl().ifPresent(builder::baseUrl);
        return builder.build();
    }
}
