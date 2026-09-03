package com.pickagent.w2.openai;

import com.openai.models.responses.ResponseInputItem;
import com.pickagent.w2.core.ToolResult;

import java.util.Objects;

/**
 * Maps a provider-neutral tool result to an OpenAI Responses function-call-output input item.
 *
 * @author jamieLu
 * @since 2026-09-02
 */
public final class OpenAiFunctionCallOutputMapper {
    /** Creates a stateless outbound mapper. */
    public OpenAiFunctionCallOutputMapper() {
    }

    /**
     * Preserves the original call identifier and output text.
     *
     * @param result validated core tool result
     * @return SDK function-call-output value
     * @throws NullPointerException when result is null
     */
    public ResponseInputItem.FunctionCallOutput map(ToolResult result) {
        Objects.requireNonNull(result, "result");
        return ResponseInputItem.FunctionCallOutput.builder()
                .callId(result.callId())
                .output(result.output())
                .build();
    }
}
