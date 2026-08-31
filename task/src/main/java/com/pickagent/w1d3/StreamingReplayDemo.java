package com.pickagent.w1d3;

import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.responses.ResponseTextDeltaEvent;
import com.openai.models.responses.ResponseTextDoneEvent;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 在本地重放固定文本流事件，验证增量聚合逻辑且不访问网络。
 *
 * @author jamieLu
 * @since 2026-08-26
 */
public final class StreamingReplayDemo {
    /** 重放事件共享的输出项标识。 */
    private static final String ITEM_ID = "replay-item-1";
    /** 所有增量合并后的预期完整文本。 */
    private static final String COMPLETE_TEXT = "Hello, Streaming 世界！";

    private StreamingReplayDemo() {
    }

    /**
     * 构造固定事件序列并输出聚合结果。
     *
     * @param args 命令行参数，本示例不使用
     */
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

    /**
     * 创建一个文本增量事件。
     *
     * @param text 本次新增的文本片段
     * @param sequenceNumber 事件序号
     * @return SDK 文本增量事件
     */
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

    /**
     * 创建文本输出完成事件。
     *
     * @param text 完整文本
     * @param sequenceNumber 事件序号
     * @return SDK 文本完成事件
     */
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
