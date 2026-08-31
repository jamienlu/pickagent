package com.pickagent.w1d2;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseUsage;

/**
 * 使用 OpenAI Java SDK 调用 DeepSeek 兼容端点的 W1D2 集成示例。
 *
 * <p>运行会产生真实网络请求，凭据仅从 {@code DEEPSEEK_KEY} 环境变量读取。</p>
 *
 * @author jamieLu
 * @since 2026-08-25
 */
public class OpenAiResponseDemo {
    /** 禁止实例化网络调用示例。 */
    private OpenAiResponseDemo() {
    }

    /**
     * 发起一次非流式请求并输出 Token 用量及文本结果。
     *
     * @param args 命令行参数，本示例不使用
     * @throws RuntimeException 客户端配置、网络调用或供应商响应失败时抛出
     */
    public static void main(String[] args) {
        OpenAIClient client = OpenAIOkHttpClient.builder().apiKey(System.getenv("DEEPSEEK_KEY")).baseUrl("https://api.deepseek.com").build();
        ResponseCreateParams params = ResponseRequestFactory.builder()
                .input("1+1=?").model("deepseek-v4-flash").maxOutputTokens(100).build()
                .builderResponseParams();

        Response response = client.responses().create(params);
        ResponseUsage responseUsage = response.usage().get();
        System.out.println("inputtokens:" + responseUsage.inputTokens());
        System.out.println("outputtokens:" + responseUsage.outputTokens());
        System.out.println("totaltokens:" + responseUsage.totalTokens());
        response.output().stream()
                .flatMap(item -> {
                    return item.message().stream();
                })
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .forEach(outputText -> System.out.println("outputText:" +outputText.text()));
    }
}
