package io.github.jamielu.agent.openai;

import com.openai.client.OpenAIClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import io.github.jamielu.agent.api.AgentContext;
import io.github.jamielu.agent.api.AgentDecision;
import io.github.jamielu.agent.api.ToolDefinition;
import io.github.jamielu.agent.runtime.AgentModelPort;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Stateful, single-run {@link AgentModelPort} backed by the OpenAI Responses API.
 *
 * <p>The adapter uses {@code previous_response_id} for continuation and sends
 * only the matching {@code function_call_output} on the next request. It sets
 * {@code parallel_tool_calls=false} so every provider turn fits the core port's
 * zero-or-one-call decision contract. Instructions and tools are repeated on
 * every request.</p>
 *
 * <p>Create one instance per {@link io.github.jamielu.agent.runtime.AgentRuntime#run(String)}
 * with {@link io.github.jamielu.agent.runtime.AgentRuntime#withModelFactory}.
 * Instances are deliberately stateful and must not be shared by concurrent runs.</p>
 */
public final class OpenAiResponsesModel implements AgentModelPort {
    private final OpenAiResponsesTransport transport;
    private final String model;
    private final Optional<String> instructions;
    private final OpenAiFunctionToolMapper toolMapper = new OpenAiFunctionToolMapper();
    private final OpenAiFunctionCallMapper callMapper = new OpenAiFunctionCallMapper();
    private final OpenAiFunctionCallOutputMapper outputMapper = new OpenAiFunctionCallOutputMapper();

    private String initialInput;
    private List<ToolDefinition> initialTools = List.of();
    private String previousResponseId;
    private String pendingCallId;
    private int historySizeAtDecision;

    /**
     * Creates an adapter using a configured blocking SDK client.
     *
     * @param client configured OpenAI client
     * @param model non-blank Responses model identifier
     * @param instructions optional non-blank instructions repeated every turn
     */
    public OpenAiResponsesModel(OpenAIClient client, String model, Optional<String> instructions) {
        this(OpenAiResponsesTransport.fromClient(client), model, instructions);
    }

    /**
     * Creates an adapter using an injectable transport.
     *
     * @param transport Responses create boundary
     * @param model non-blank Responses model identifier
     * @param instructions optional non-blank instructions repeated every turn
     */
    public OpenAiResponsesModel(
            OpenAiResponsesTransport transport,
            String model,
            Optional<String> instructions) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.model = requireNonBlank(model, "model");
        this.instructions = Objects.requireNonNull(instructions, "instructions")
                .map(value -> requireNonBlank(value, "instructions"));
    }

    /**
     * Creates an adapter without request-level instructions.
     *
     * @param client configured OpenAI client
     * @param model non-blank Responses model identifier
     */
    public OpenAiResponsesModel(OpenAIClient client, String model) {
        this(client, model, Optional.empty());
    }

    /**
     * Creates an injectable adapter without request-level instructions.
     *
     * @param transport Responses create boundary
     * @param model non-blank Responses model identifier
     */
    public OpenAiResponsesModel(OpenAiResponsesTransport transport, String model) {
        this(transport, model, Optional.empty());
    }

    /**
     * Sends an initial request or a call-output continuation based on the
     * immutable Runtime context.
     */
    @Override
    public AgentDecision decide(AgentContext context) {
        Objects.requireNonNull(context, "context");
        ResponseCreateParams params = context.history().isEmpty()
                ? initialParams(context)
                : continuationParams(context);

        Response response = Objects.requireNonNull(
                transport.create(params), "transport returned null response");
        if (response.id().isBlank()) {
            throw new OpenAiResponsesModelException(
                    OpenAiResponsesModelException.Reason.INVALID_RESPONSE_ID,
                    "Responses API returned a blank response id");
        }

        AgentDecision decision = mapDecision(response.output());
        previousResponseId = response.id();
        historySizeAtDecision = context.history().size();
        pendingCallId = decision instanceof AgentDecision.ToolCall call
                ? call.callId()
                : null;
        return decision;
    }

    private ResponseCreateParams initialParams(AgentContext context) {
        initialInput = context.input();
        initialTools = List.copyOf(context.tools());
        previousResponseId = null;
        pendingCallId = null;
        historySizeAtDecision = 0;
        return baseParams(context.tools()).input(context.input()).build();
    }

    private ResponseCreateParams continuationParams(AgentContext context) {
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
        return baseParams(context.tools())
                .previousResponseId(previousResponseId)
                .inputOfResponse(List.of(ResponseInputItem.ofFunctionCallOutput(output)))
                .build();
    }

    private ResponseCreateParams.Builder baseParams(List<ToolDefinition> tools) {
        ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
                .model(model)
                .parallelToolCalls(false)
                .store(true);
        instructions.ifPresent(builder::instructions);
        tools.stream().map(toolMapper::map).forEach(builder::addTool);
        return builder;
    }

    private AgentDecision mapDecision(List<ResponseOutputItem> outputItems) {
        List<ResponseOutputItem> snapshot = List.copyOf(
                Objects.requireNonNull(outputItems, "response output"));
        boolean hasFunctionCall = snapshot.stream().anyMatch(ResponseOutputItem::isFunctionCall);
        if (hasFunctionCall) {
            // Validates the complete heterogeneous output before the Runtime can execute a handler.
            new OpenAiResponseLedger().prepare(snapshot);
            return callMapper.map(snapshot);
        }

        for (ResponseOutputItem item : snapshot) {
            if (!item.isReasoning() && !item.isMessage()) {
                throw new OpenAiResponsesModelException(
                        OpenAiResponsesModelException.Reason.UNEXPECTED_FINAL_OUTPUT_ITEM,
                        "terminal response contains unsupported output item: "
                                + OpenAiResponseLedger.itemType(item));
            }
        }
        String answer = snapshot.stream()
                .filter(ResponseOutputItem::isMessage)
                .flatMap(item -> item.asMessage().content().stream())
                .filter(content -> content.outputText().isPresent())
                .map(content -> content.asOutputText().text())
                .reduce("", String::concat);
        if (answer.isBlank()) {
            throw new OpenAiResponsesModelException(
                    OpenAiResponsesModelException.Reason.MISSING_FINAL_TEXT,
                    "terminal response did not contain output text");
        }
        return new AgentDecision.FinalAnswer(answer);
    }

    private static OpenAiResponsesModelException contextMismatch(String message) {
        return new OpenAiResponsesModelException(
                OpenAiResponsesModelException.Reason.CONTEXT_MISMATCH, message);
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be null or blank");
        }
        return value;
    }
}
