package com.pickagent.w1d2;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseUsage;

/**
 * @author jamieLu
 * @create 2026-08-25
 */
public class OpenAiResponseDemo {
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
