package com.pickagent.w1d3;

import com.openai.models.responses.ResponseStreamEvent;
import lombok.Getter;

/**
 * 按接收顺序聚合 Responses API 流式文本增量。
 *
 * <p>{@code output_text.done} 只标记终止状态，不会重复追加完整文本。</p>
 *
 * @author jamieLu
 * @since 2026-08-26
 */
public final class StreamTextAccumulator {
    /** 已接收文本的可变缓冲区。 */
    @Getter
    private final StringBuilder text = new StringBuilder();
    /** 是否已经收到文本输出完成事件。 */
    @Getter
    private boolean outputTextDone;

    /** 创建空的流式文本聚合器。 */
    public StreamTextAccumulator() {
    }

    /**
     * 接收一个流事件；文本增量会被追加，完成事件只更新终态标记。
     *
     * @param event SDK 流事件，传入 {@code null} 时忽略
     */
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
