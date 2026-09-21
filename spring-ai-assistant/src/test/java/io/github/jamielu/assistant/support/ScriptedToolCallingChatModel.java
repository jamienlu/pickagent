package io.github.jamielu.assistant.support;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** 以固定两轮脚本模拟“工具请求—工具结果—最终回答”的离线模型。 */
public final class ScriptedToolCallingChatModel implements ChatModel {
    private static final String TOOL_TYPE = "function";

    private final String callId;
    private final String toolName;
    private final String arguments;
    private final String expectedToolResult;
    private final String finalAnswer;
    private final AtomicInteger calls = new AtomicInteger();
    private final List<Prompt> prompts = new CopyOnWriteArrayList<>();

    /**
     * 创建一个确定性的两轮工具调用脚本。
     *
     * @param callId 工具调用关联标识
     * @param toolName 第一轮请求的工具名称
     * @param arguments 第一轮请求的 JSON 参数
     * @param expectedToolResult 第二轮必须收到的工具结果
     * @param finalAnswer 校验工具结果后返回的最终回答
     */
    public ScriptedToolCallingChatModel(
            String callId,
            String toolName,
            String arguments,
            String expectedToolResult,
            String finalAnswer) {
        this.callId = Objects.requireNonNull(callId, "callId");
        this.toolName = Objects.requireNonNull(toolName, "toolName");
        this.arguments = Objects.requireNonNull(arguments, "arguments");
        this.expectedToolResult = Objects.requireNonNull(expectedToolResult, "expectedToolResult");
        this.finalAnswer = Objects.requireNonNull(finalAnswer, "finalAnswer");
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        this.prompts.add(Objects.requireNonNull(prompt, "prompt"));
        int turn = this.calls.incrementAndGet();
        if (turn == 1) {
            return toolCallResponse();
        }
        if (turn == 2) {
            requireExpectedToolResult(prompt);
            return textResponse(this.finalAnswer);
        }
        throw new IllegalStateException("script supports exactly two model calls");
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.error(new UnsupportedOperationException("scripted tool loop is synchronous only"));
    }

    /** 返回支持工具回调的基础选项，使离线模型遵循真实工具协议。 */
    @Override
    public ChatOptions getOptions() {
        return ToolCallingChatOptions.builder().build();
    }

    /** 返回模型实际收到的每一轮 Prompt 的不可变快照。 */
    public List<Prompt> prompts() {
        return List.copyOf(this.prompts);
    }

    /** 返回模型同步调用次数。 */
    public int calls() {
        return this.calls.get();
    }

    private ChatResponse toolCallResponse() {
        var toolCall = new AssistantMessage.ToolCall(
                this.callId,
                TOOL_TYPE,
                this.toolName,
                this.arguments);
        var assistantMessage = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(toolCall))
                .build();
        return new ChatResponse(List.of(new Generation(assistantMessage)));
    }

    private void requireExpectedToolResult(Prompt prompt) {
        var responseMessages = prompt.getInstructions().stream()
                .filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast)
                .toList();
        if (responseMessages.size() != 1) {
            throw new IllegalStateException("second prompt must contain exactly one tool response message");
        }
        List<ToolResponseMessage.ToolResponse> responses = responseMessages.getFirst().getResponses();
        if (responses.size() != 1) {
            throw new IllegalStateException("tool response message must contain exactly one result");
        }
        ToolResponseMessage.ToolResponse response = responses.getFirst();
        if (!this.callId.equals(response.id())
                || !this.toolName.equals(response.name())
                || !this.expectedToolResult.equals(response.responseData())) {
            throw new IllegalStateException("second prompt contains an unexpected tool result: " + response);
        }
    }

    private static ChatResponse textResponse(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}
