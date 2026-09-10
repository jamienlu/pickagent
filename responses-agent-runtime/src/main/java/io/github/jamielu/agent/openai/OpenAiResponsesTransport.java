package io.github.jamielu.agent.openai;

import com.openai.client.OpenAIClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;

import java.util.Objects;

/** 创建 OpenAI Responses 的最小阻塞传输边界。 */
@FunctionalInterface
public interface OpenAiResponsesTransport {
    /**
     * 发送一次 Responses 创建请求。
     *
     * @param params 已完整构建的 SDK 请求参数
     * @return SDK 响应
     */
    Response create(ResponseCreateParams params);

    /**
     * 适配阻塞式 OpenAI Java SDK 客户端。
     *
     * @param client 已配置 SDK 客户端
     * @return 映射 SDK 异常的阻塞传输边界
     */
    static OpenAiResponsesTransport fromClient(OpenAIClient client) {
        OpenAIClient checked = Objects.requireNonNull(client, "client");
        OpenAiExceptionMapper exceptionMapper = new OpenAiExceptionMapper();
        return params -> {
            try {
                return checked.responses().create(params);
            } catch (com.openai.errors.OpenAIException failure) {
                throw exceptionMapper.map(failure);
            }
        };
    }
}
