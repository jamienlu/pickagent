package com.pickagent.w1d4;

import java.util.Objects;
import java.util.function.Function;

public final class StructuredResponseHandler {
    private final Function<String, DecodeResult> decoder;

    public StructuredResponseHandler() {
        this(new StructuredOutputDecoder()::decode);
    }

    public StructuredResponseHandler(Function<String, DecodeResult> decoder) {
        this.decoder = Objects.requireNonNull(decoder, "decoder");
    }

    public HandlingResult handle(ResponseSnapshot response) {
        Objects.requireNonNull(response, "response");

        if (response.status() == ResponseStatus.INCOMPLETE) {
            return new HandlingResult.Incomplete(response.incompleteReason());
        }

        if (response.content() instanceof ResponseContent.Refusal refusal) {
            return new HandlingResult.Refused(refusal.reason());
        }

        ResponseContent.OutputText outputText = (ResponseContent.OutputText) response.content();
        DecodeResult decodeResult = decoder.apply(outputText.text());
        if (decodeResult instanceof DecodeResult.Success success) {
            return new HandlingResult.EventAccepted(success.event());
        }
        return new HandlingResult.InvalidOutput((DecodeResult.Failure) decodeResult);
    }

    public enum ResponseStatus {
        COMPLETED,
        INCOMPLETE
    }

    public sealed interface ResponseContent permits ResponseContent.OutputText, ResponseContent.Refusal {
        record OutputText(String text) implements ResponseContent {
            public OutputText {
                Objects.requireNonNull(text, "text");
            }
        }

        record Refusal(String reason) implements ResponseContent {
            public Refusal {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    public record ResponseSnapshot(
            ResponseStatus status,
            ResponseContent content,
            String incompleteReason
    ) {
        public ResponseSnapshot {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(content, "content");
            if (status == ResponseStatus.INCOMPLETE) {
                Objects.requireNonNull(incompleteReason, "incompleteReason");
            }
        }

        public static ResponseSnapshot completed(String outputText) {
            return new ResponseSnapshot(
                    ResponseStatus.COMPLETED,
                    new ResponseContent.OutputText(outputText),
                    null
            );
        }

        public static ResponseSnapshot refusal(String reason) {
            return new ResponseSnapshot(
                    ResponseStatus.COMPLETED,
                    new ResponseContent.Refusal(reason),
                    null
            );
        }

        public static ResponseSnapshot incomplete(String reason, String partialOutputText) {
            return new ResponseSnapshot(
                    ResponseStatus.INCOMPLETE,
                    new ResponseContent.OutputText(partialOutputText == null ? "" : partialOutputText),
                    reason
            );
        }
    }

    public sealed interface HandlingResult permits HandlingResult.EventAccepted,
            HandlingResult.Refused, HandlingResult.Incomplete, HandlingResult.InvalidOutput {

        record EventAccepted(Event event) implements HandlingResult {
            public EventAccepted {
                Objects.requireNonNull(event, "event");
            }
        }

        record Refused(String reason) implements HandlingResult {
            public Refused {
                Objects.requireNonNull(reason, "reason");
            }
        }

        record Incomplete(String reason) implements HandlingResult {
            public Incomplete {
                Objects.requireNonNull(reason, "reason");
            }
        }

        record InvalidOutput(DecodeResult.Failure failure) implements HandlingResult {
            public InvalidOutput {
                Objects.requireNonNull(failure, "failure");
            }
        }
    }
}
