package io.github.jamielu.agent.openai;

import com.openai.client.OpenAIClient;
import com.openai.models.responses.Response;
import io.github.jamielu.agent.api.AgentContext;
import io.github.jamielu.agent.api.AgentDecision;
import io.github.jamielu.agent.runtime.AgentModelPort;

import java.util.Objects;
import java.util.Optional;

/**
 * 基于 OpenAI Responses API 的单次运行有状态模型适配器。
 *
 * <p>适配器使用 {@code previous_response_id} 续接，并在下一请求中只发送匹配的
 * {@code function_call_output}。它固定 {@code parallel_tool_calls=false}，使每轮供应商
 * 响应符合核心端口“零或一个调用”的契约；每轮都会重新发送指令和工具定义。</p>
 *
 * <p>每次 {@link io.github.jamielu.agent.runtime.AgentRuntime#run(String)} 应通过
 * {@link io.github.jamielu.agent.runtime.AgentRuntime#withModelFactory} 创建一个实例。
 * 实例刻意保存协议状态，不得由并发运行共享。</p>
 */
public final class OpenAiResponsesModel implements AgentModelPort {
    private final OpenAiResponsesTransport transport;
    private final OpenAiConversation conversation;
    private final OpenAiResponseDecoder decoder = new OpenAiResponseDecoder();

    /**
     * 使用已配置的阻塞式 SDK 客户端创建适配器。
     *
     * @param client 已配置 SDK 客户端
     * @param model 非空白模型标识
     * @param instructions 每轮重复发送的可选指令
     */
    public OpenAiResponsesModel(OpenAIClient client, String model, Optional<String> instructions) {
        this(OpenAiResponsesTransport.fromClient(client), model, instructions,
                OpenAiResponseOptions.defaults());
    }

    /**
     * 使用已配置 SDK 客户端和显式请求选项创建适配器。
     *
     * @param client 已配置 SDK 客户端
     * @param model 非空白模型标识
     * @param instructions 每轮重复发送的可选指令
     * @param options 输出和存储选项
     */
    public OpenAiResponsesModel(
            OpenAIClient client,
            String model,
            Optional<String> instructions,
            OpenAiResponseOptions options) {
        this(OpenAiResponsesTransport.fromClient(client), model, instructions, options);
    }

    /**
     * 使用可注入传输边界创建适配器。
     *
     * @param transport Responses 创建边界
     * @param model 非空白模型标识
     * @param instructions 每轮重复发送的可选指令
     */
    public OpenAiResponsesModel(
            OpenAiResponsesTransport transport,
            String model,
            Optional<String> instructions) {
        this(transport, model, instructions, OpenAiResponseOptions.defaults());
    }

    /**
     * 使用可注入传输边界和显式请求选项创建适配器。
     *
     * @param transport Responses 创建边界
     * @param model 非空白 Responses 模型标识
     * @param instructions 每轮重复发送的可选非空白指令
     * @param options 输出和存储选项
     */
    public OpenAiResponsesModel(
            OpenAiResponsesTransport transport,
            String model,
            Optional<String> instructions,
            OpenAiResponseOptions options) {
        this.transport = Objects.requireNonNull(transport, "transport");
        String checkedModel = requireNonBlank(model, "model");
        Optional<String> checkedInstructions = Objects.requireNonNull(instructions, "instructions")
                .map(value -> requireNonBlank(value, "instructions"));
        this.conversation = new OpenAiConversation(
                checkedModel, checkedInstructions, Objects.requireNonNull(options, "options"));
    }

    /**
     * 创建不携带请求级指令的 SDK 适配器。
     *
     * @param client 已配置 SDK 客户端
     * @param model 非空白模型标识
     */
    public OpenAiResponsesModel(OpenAIClient client, String model) {
        this(client, model, Optional.empty());
    }

    /**
     * 创建不携带请求级指令的可注入适配器。
     *
     * @param transport Responses 创建边界
     * @param model 非空白模型标识
     */
    public OpenAiResponsesModel(OpenAiResponsesTransport transport, String model) {
        this(transport, model, Optional.empty());
    }

    /** 根据不可变运行时上下文发送首轮请求或工具结果续接请求。 */
    @Override
    public AgentDecision decide(AgentContext context) {
        Objects.requireNonNull(context, "context");
        Response response = Objects.requireNonNull(
                transport.create(conversation.nextRequest(context)),
                "transport returned null response");
        if (response.id().isBlank()) {
            throw new OpenAiResponsesModelException(
                    OpenAiResponsesModelException.Reason.INVALID_RESPONSE_ID,
                    "Responses API returned a blank response id");
        }

        AgentDecision decision = decoder.decode(response.output());
        conversation.accept(response.id(), decision, context.history().size());
        return decision;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be null or blank");
        }
        return value;
    }
}
