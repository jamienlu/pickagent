package com.pickagent.w1d5.core;

import java.util.Objects;

@FunctionalInterface
public interface EventParser {
    ParseResult parse(String content);

    sealed interface ParseResult permits ParseResult.Parsed, ParseResult.Invalid {
        record Parsed(EventData event) implements ParseResult {
            public Parsed {
                Objects.requireNonNull(event, "event");
            }
        }

        record Invalid(String reason) implements ParseResult {
            public Invalid {
                if (reason == null || reason.isBlank()) {
                    throw new IllegalArgumentException("parse failure reason cannot be null or blank");
                }
            }
        }
    }
}
