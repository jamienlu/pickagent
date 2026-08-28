package com.pickagent.w1d5.infrastructure;

import com.pickagent.w1d4.DecodeResult;
import com.pickagent.w1d4.StructuredOutputDecoder;
import com.pickagent.w1d5.core.EventData;
import com.pickagent.w1d5.core.EventParser;

public final class StructuredEventParser implements EventParser {
    private final StructuredOutputDecoder decoder = new StructuredOutputDecoder();

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
