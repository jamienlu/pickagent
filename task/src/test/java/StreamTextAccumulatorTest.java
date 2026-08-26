import com.openai.models.responses.ResponseErrorEvent;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.responses.ResponseTextDeltaEvent;
import com.openai.models.responses.ResponseTextDoneEvent;
import com.pickagent.w1d3.StreamTextAccumulator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
@Tag("integration")
class StreamTextAccumulatorTest {

    @Test
    void mergesMultipleDeltasInOrder() {
        StreamTextAccumulator accumulator = new StreamTextAccumulator();

        accumulator.accept(delta("Hello"));
        accumulator.accept(delta(", "));
        accumulator.accept(delta("world"));

        assertEquals("Hello, world", accumulator.getText().toString());
    }

    @Test
    void preservesChineseAndEnglishFragments() {
        StreamTextAccumulator accumulator = new StreamTextAccumulator();

        accumulator.accept(delta("你好，"));
        accumulator.accept(delta("OpenAI"));
        accumulator.accept(delta("！"));

        assertEquals("你好，OpenAI！", accumulator.getText().toString());
    }

    @Test
    void doneEventDoesNotAppendFullTextAgain() {
        StreamTextAccumulator accumulator = new StreamTextAccumulator();

        accumulator.accept(delta("Hello"));
        accumulator.accept(done("Hello"));

        assertEquals("Hello", accumulator.getText().toString());
        assertTrue(accumulator.isOutputTextDone());
    }

    @Test
    void safelyIgnoresUnknownAndNullEvents() {
        StreamTextAccumulator accumulator = new StreamTextAccumulator();
        ResponseStreamEvent error = ResponseStreamEvent.ofError(
                ResponseErrorEvent.builder()
                        .code("test_error")
                        .message("test error")
                        .param(java.util.Optional.empty())
                        .sequenceNumber(1)
                        .build()
        );

        assertDoesNotThrow(() -> accumulator.accept(error));
        assertDoesNotThrow(() -> accumulator.accept(null));
        assertEquals("", accumulator.getText().toString());
    }

    private static ResponseStreamEvent delta(String value) {
        return ResponseStreamEvent.ofOutputTextDelta(
                ResponseTextDeltaEvent.builder()
                        .contentIndex(0)
                        .delta(value)
                        .itemId("item-1")
                        .logprobs(List.of())
                        .outputIndex(0)
                        .sequenceNumber(1)
                        .build()
        );
    }

    private static ResponseStreamEvent done(String fullText) {
        return ResponseStreamEvent.ofOutputTextDone(
                ResponseTextDoneEvent.builder()
                        .contentIndex(0)
                        .itemId("item-1")
                        .logprobs(List.of())
                        .outputIndex(0)
                        .sequenceNumber(2)
                        .text(fullText)
                        .build()
        );
    }
}
