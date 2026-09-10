package io.github.jamielu.agent.openai;

import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.errors.OpenAIIoException;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ToolChoiceOptions;
import com.openai.services.blocking.ResponseService;
import io.github.jamielu.agent.reliability.FailureKind;
import io.github.jamielu.agent.runtime.ModelExecutionException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiResponsesTransportTest {
    // 场景：SDK Responses 服务正常返回；行为：通过生产传输桥调用；预期：请求和响应对象原样委派。
    @Test
    void delegatesRequestToSdkResponseService() {
        Response expected = response();
        ResponseCreateParams params = ResponseCreateParams.builder()
                .model("gpt-test").input("hello").build();
        OpenAiResponsesTransport transport = OpenAiResponsesTransport.fromClient(
                client(service((method, argument) -> {
                    assertEquals("create", method);
                    assertSame(params, argument);
                    return expected;
                })));

        assertSame(expected, transport.create(params));
    }

    // 场景：SDK Responses 服务抛出传输异常；行为：通过生产传输桥调用；预期：异常被映射为核心模型失败。
    @Test
    void mapsSdkFailureAtTransportBoundary() {
        OpenAIIoException source = new OpenAIIoException("socket failed");
        OpenAiResponsesTransport transport = OpenAiResponsesTransport.fromClient(
                client(service((method, argument) -> { throw source; })));

        ModelExecutionException failure = assertThrows(
                ModelExecutionException.class,
                () -> transport.create(ResponseCreateParams.builder()
                        .model("gpt-test").input("hello").build()));

        assertEquals(FailureKind.TIMEOUT, failure.kind());
        assertSame(source, failure.getCause());
    }

    // 场景：生产传输桥收到空客户端；行为：创建适配器；预期：立即拒绝空依赖。
    @Test
    void rejectsNullClient() {
        assertThrows(NullPointerException.class,
                () -> OpenAiResponsesTransport.fromClient(null));
    }

    private static OpenAIClient client(ResponseService responses) {
        return (OpenAIClient) Proxy.newProxyInstance(
                OpenAIClient.class.getClassLoader(),
                new Class<?>[]{OpenAIClient.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("responses")) {
                        return responses;
                    }
                    if (method.getName().equals("close")) {
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static ResponseService service(ServiceCall call) {
        return (ResponseService) Proxy.newProxyInstance(
                ResponseService.class.getClassLoader(),
                new Class<?>[]{ResponseService.class},
                (proxy, method, args) -> call.invoke(method.getName(), args[0]));
    }

    private static Response response() {
        return Response.builder()
                .id("resp_test")
                .createdAt(1.0)
                .error(Optional.empty())
                .incompleteDetails(Optional.empty())
                .instructions(Optional.empty())
                .metadata(Optional.empty())
                .model("gpt-test")
                .object_(JsonValue.from("response"))
                .output(List.of())
                .parallelToolCalls(false)
                .temperature(Optional.empty())
                .toolChoice(ToolChoiceOptions.AUTO)
                .tools(List.of())
                .topP(Optional.empty())
                .build();
    }

    @FunctionalInterface
    private interface ServiceCall {
        Object invoke(String method, Object argument);
    }
}
