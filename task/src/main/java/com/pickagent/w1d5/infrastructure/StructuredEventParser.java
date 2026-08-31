package com.pickagent.w1d5.infrastructure;

import com.pickagent.w1d4.DecodeResult;
import com.pickagent.w1d4.StructuredOutputDecoder;
import com.pickagent.w1d5.core.EventData;
import com.pickagent.w1d5.core.EventParser;

/**
 * 使用 W1D4 防御式解码器实现 W1D5 事件解析端口的基础设施 adapter。
 *
 * @author jamieLu
 * @since 2026-08-28
 */
public final class StructuredEventParser implements EventParser {
    /** 负责 JSON 结构和 Event 契约校验的解码器。 */
    private final StructuredOutputDecoder decoder = new StructuredOutputDecoder();

    /** 创建使用默认 W1D4 解码器的事件解析 adapter。 */
    public StructuredEventParser() {
    }

    /**
     * 将模型完成文本解析为核心事件数据。
     *
     * @param content 模型完成文本
     * @return 解析成功事件或包含解码错误的无效结果
     */
    @Override
    public ParseResult parse(String content) {
        DecodeResult decoded = decoder.decode(content);
        if (decoded instanceof DecodeResult.Success success) {
            var event = success.event();
            return new ParseResult.Parsed(
                    new EventData(event.name(), event.date(), event.participants())
            );
        }

        DecodeResult.Failure failure = (DecodeResult.Failure) decoded;
        return new ParseResult.Invalid(failure.code() + ": " + failure.reason());
    }
}
