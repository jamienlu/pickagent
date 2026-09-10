package io.github.jamielu.agent.openai;

import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputItem;
import io.github.jamielu.agent.api.AgentContext;
import io.github.jamielu.agent.api.AgentDecision;
import io.github.jamielu.agent.api.ToolDefinition;

import java.util.List;
import java.util.Optional;

/** 管理单次 Responses 工具循环的请求构建与续接状态。 */
final class OpenAiConversation {
    private final String model;
    private final Optional<String> instructions;
    private final OpenAiResponseOptions options;
    private final OpenAiFunctionToolMapper toolMapper = new OpenAiFunctionToolMapper();
    private final OpenAiFunctionCallOutputMapper outputMapper = new OpenAiFunctionCallOutputMapper();

    private String initialInput;
    private List<ToolDefinition> initialTools = List.of();
    private String previousResponseId;
    private String pendingCallId;
    private int historySizeAtDecision;

    /** 创建单次运行会话状态。 */
    OpenAiConversation(
            String model,
            Optional<String> instructions,
            OpenAiResponseOptions options) {
        this.model = model;
        this.instructions = instructions;
        this.options = options;
    }

    /** 根据运行时上下文创建首轮或续接请求。 */
    ResponseCreateParams nextRequest(AgentContext context) {
        return context.history().isEmpty()
                ? initialRequest(context)
                : continuationRequest(context);
    }

    /** 在成功解码响应后提交新的供应商续接状态。 */
    void accept(String responseId, AgentDecision decision, int historySize) {
        previousResponseId = responseId;
        historySizeAtDecision = historySize;
        pendingCallId = decision instanceof AgentDecision.ToolCall call
                ? call.callId()
                : null;
    }

    private ResponseCreateParams initialRequest(AgentContext context) {
        initialInput = context.input();
        initialTools = List.copyOf(context.tools());
        previousResponseId = null;
        pendingCallId = null;
        historySizeAtDecision = 0;
        return baseRequest(context.tools()).input(context.input()).build();
    }

    private ResponseCreateParams continuationRequest(AgentContext context) {
        if (previousResponseId == null || pendingCallId == null) {
            throw contextMismatch("no function call is pending for continuation");
        }
        if (!context.input().equals(initialInput) || !context.tools().equals(initialTools)) {
            throw contextMismatch("input or tool definitions changed during one run");
        }
        if (context.history().size() != historySizeAtDecision + 1) {
            throw contextMismatch("expected exactly one new tool exchange");
        }
        AgentContext.Exchange exchange = context.history().getLast();
        if (!exchange.call().callId().equals(pendingCallId)) {
            throw contextMismatch("tool history does not match pending callId: " + pendingCallId);
        }

        ResponseInputItem.FunctionCallOutput output = outputMapper.map(exchange.result());
        return baseRequest(context.tools())
                .previousResponseId(previousResponseId)
                .inputOfResponse(List.of(ResponseInputItem.ofFunctionCallOutput(output)))
                .build();
    }

    private ResponseCreateParams.Builder baseRequest(List<ToolDefinition> tools) {
        ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
                .model(model)
                .parallelToolCalls(false)
                .maxOutputTokens(options.maxOutputTokens())
                .store(options.store());
        instructions.ifPresent(builder::instructions);
        tools.stream().map(toolMapper::map).forEach(builder::addTool);
        return builder;
    }

    private static OpenAiResponsesModelException contextMismatch(String message) {
        return new OpenAiResponsesModelException(
                OpenAiResponsesModelException.Reason.CONTEXT_MISMATCH, message);
    }
}
