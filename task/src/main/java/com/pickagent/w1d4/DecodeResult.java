package com.pickagent.w1d4;

import java.util.Objects;

public sealed interface DecodeResult permits DecodeResult.Success, DecodeResult.Failure {

    record Success(Event event) implements DecodeResult {
        public Success {
            Objects.requireNonNull(event, "event");
        }
    }

    record Failure(ErrorCode code, String reason) implements DecodeResult {
        public Failure {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(reason, "reason");
        }
    }

    enum ErrorCode {
        EMPTY_TEXT,
        MALFORMED_JSON,
        NOT_JSON_OBJECT,
        MISSING_FIELDS,
        EXTRA_FIELDS,
        WRONG_FIELD_TYPE,
        CONVERSION_FAILED
    }
}
