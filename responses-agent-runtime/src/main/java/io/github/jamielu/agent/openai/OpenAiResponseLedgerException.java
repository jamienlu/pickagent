package io.github.jamielu.agent.openai;

import java.util.Objects;

/**
 * Indicates that a Responses output cannot be continued without losing or
 * mis-associating protocol state.
 */
public final class OpenAiResponseLedgerException extends IllegalArgumentException {
    /** Stable protocol failure category. */
    private final Reason reason;

    /**
     * Creates a protocol-ledger failure.
     *
     * @param reason stable failure category
     * @param message diagnostic detail
     */
    public OpenAiResponseLedgerException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /**
     * Returns the machine-readable failure category.
     *
     * @return ledger failure category
     */
    public Reason reason() {
        return reason;
    }

    /** Fail-fast reasons enforced before a continuation request is built. */
    public enum Reason {
        /** No function call exists to receive the supplied result. */
        NO_FUNCTION_CALL,
        /** A function call correlation id was blank. */
        INVALID_CALL_ID,
        /** A function call correlation id appeared more than once. */
        DUPLICATE_CALL_ID,
        /** The completed result batch did not contain one result per call. */
        RESULT_COUNT_MISMATCH,
        /** The result references a different call than the output history. */
        CALL_ID_MISMATCH,
        /** An output item is outside the ledger's explicitly supported set. */
        UNKNOWN_OUTPUT_ITEM,
        /** The final replay response contains a non-final protocol item. */
        UNEXPECTED_FINAL_OUTPUT_ITEM
    }
}


