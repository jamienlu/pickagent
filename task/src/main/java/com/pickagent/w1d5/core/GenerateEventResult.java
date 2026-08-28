package com.pickagent.w1d5.core;

import java.util.Objects;

public sealed interface GenerateEventResult permits GenerateEventResult.Generated,
        GenerateEventResult.Refused, GenerateEventResult.Incomplete, GenerateEventResult.Failed {

    record Generated(EventData event) implements GenerateEventResult {
        public Generated {
            Objects.requireNonNull(event, "event");
        }
    }

    record Refused(String reason) implements GenerateEventResult {
        public Refused {
            Objects.requireNonNull(reason, "reason");
        }
    }

    record Incomplete(String reason) implements GenerateEventResult {
        public Incomplete {
            Objects.requireNonNull(reason, "reason");
        }
    }

    record Failed(FailureKind kind, String reason) implements GenerateEventResult {
        public Failed {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(reason, "reason");
        }
    }

    enum FailureKind {
        GATEWAY_TRANSPORT,
        GATEWAY_PROVIDER,
        GATEWAY_PROTOCOL,
        INVALID_MODEL_OUTPUT
    }
}
