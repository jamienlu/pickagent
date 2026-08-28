package com.pickagent.w1d5.core;

import java.util.Objects;

public sealed interface ModelResult permits ModelResult.Completed, ModelResult.Refused,
        ModelResult.Incomplete, ModelResult.Failed {

    record Completed(String content) implements ModelResult {
        public Completed {
            Objects.requireNonNull(content, "content");
        }
    }

    record Refused(String reason) implements ModelResult {
        public Refused {
            reason = requireReason(reason, "refusal reason");
        }
    }

    record Incomplete(String reason) implements ModelResult {
        public Incomplete {
            reason = requireReason(reason, "incomplete reason");
        }
    }

    record Failed(FailureKind kind, String reason) implements ModelResult {
        public Failed {
            Objects.requireNonNull(kind, "kind");
            reason = requireReason(reason, "failure reason");
        }
    }

    enum FailureKind {
        TRANSPORT,
        PROVIDER,
        PROTOCOL
    }

    private static String requireReason(String reason, String fieldName) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
        return reason;
    }
}
