package io.github.jamielu.agent.openai;

import java.util.Objects;

/** A typed failure in the stateful Responses model adapter contract. */
public final class OpenAiResponsesModelException extends IllegalStateException {
    /** Stable category that callers can branch on without parsing the message. */
    private final Reason reason;

    /**
     * Creates an adapter failure.
     *
     * @param reason stable machine-readable category
     * @param message diagnostic detail
     */
    public OpenAiResponsesModelException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /**
     * Returns the stable failure category.
     *
     * @return machine-readable adapter failure reason
     */
    public Reason reason() {
        return reason;
    }

    /** Adapter failures that callers can handle without parsing messages. */
    public enum Reason {
        /** A continuation context does not match the adapter's pending call. */
        CONTEXT_MISMATCH,
        /** The provider returned a blank response identifier. */
        INVALID_RESPONSE_ID,
        /** A terminal response did not contain visible output text. */
        MISSING_FINAL_TEXT,
        /** A terminal response included an unsupported output item. */
        UNEXPECTED_FINAL_OUTPUT_ITEM
    }
}
