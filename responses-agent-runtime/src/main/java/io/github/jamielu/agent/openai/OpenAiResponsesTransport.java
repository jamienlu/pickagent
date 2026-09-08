package io.github.jamielu.agent.openai;

import com.openai.client.OpenAIClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;

import java.util.Objects;

/**
 * Minimal blocking transport boundary for creating OpenAI Responses.
 *
 * <p>The functional shape keeps request construction testable without network
 * access while {@link #fromClient(OpenAIClient)} provides the production SDK
 * bridge.</p>
 */
@FunctionalInterface
public interface OpenAiResponsesTransport {
    /**
     * Sends one Responses create request.
     *
     * @param params complete SDK request parameters
     * @return non-null SDK response
     */
    Response create(ResponseCreateParams params);

    /**
     * Adapts a blocking OpenAI Java SDK client.
     *
     * @param client configured SDK client
     * @return transport that delegates to {@code client.responses().create}
     */
    static OpenAiResponsesTransport fromClient(OpenAIClient client) {
        OpenAIClient checked = Objects.requireNonNull(client, "client");
        return params -> checked.responses().create(params);
    }
}
