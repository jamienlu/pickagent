package com.pickagent.w1d3;

import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.responses.ResponseTextDeltaEvent;
import com.openai.models.responses.ResponseTextDoneEvent;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class StreamingReplayDemo {
    private static final String ITEM_ID = "replay-item-1";
    private static final String COMPLETE_TEXT = "Hello, Streaming 世界！";

    private StreamingReplayDemo() {
    }

    public static void main(String[] args) {
        List<ResponseStreamEvent> events = List.of(
                delta("Hello", 1),
                delta(", Streaming ", 2),
                delta("世界", 3),
                delta("！", 4),
                done(COMPLETE_TEXT, 5)
        );

        StreamTextAccumulator accumulator = new StreamTextAccumulator();
        events.forEach(accumulator::accept);

        PrintStream utf8Output = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        utf8Output.println(accumulator.getText());
    }

    private static ResponseStreamEvent delta(String text, long sequenceNumber) {
        return ResponseStreamEvent.ofOutputTextDelta(
                ResponseTextDeltaEvent.builder()
                        .contentIndex(0)
                        .delta(text)
                        .itemId(ITEM_ID)
                        .logprobs(List.of())
                        .outputIndex(0)
                        .sequenceNumber(sequenceNumber)
                        .build()
        );
    }

    private static ResponseStreamEvent done(String text, long sequenceNumber) {
        return ResponseStreamEvent.ofOutputTextDone(
                ResponseTextDoneEvent.builder()
                        .contentIndex(0)
                        .itemId(ITEM_ID)
                        .logprobs(List.of())
                        .outputIndex(0)
                        .sequenceNumber(sequenceNumber)
                        .text(text)
                        .build()
        );
    }
}
