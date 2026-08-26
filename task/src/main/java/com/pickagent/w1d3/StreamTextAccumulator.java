package com.pickagent.w1d3;

import com.openai.models.responses.ResponseStreamEvent;
import lombok.Getter;

/**
 * Accumulates text fragments emitted by a streaming Responses API call.
 */
public final class StreamTextAccumulator {
    @Getter
    private final StringBuilder text = new StringBuilder();
    @Getter
    private boolean outputTextDone;

    public void accept(ResponseStreamEvent event) {
        if (event == null) {
            return;
        }

        event.outputTextDelta().ifPresent(deltaEvent -> {
            String delta = deltaEvent.delta();
            text.append(delta);
        });

        if (event.outputTextDone().isPresent()) {
            outputTextDone = true;
        }
    }

}
