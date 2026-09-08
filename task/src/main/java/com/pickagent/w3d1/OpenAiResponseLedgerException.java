package com.pickagent.w3d1;

import java.util.Objects;

/**
 * Indicates that a Responses output cannot be continued without losing or
 * mis-associating protocol state.
 */
public final class OpenAiResponseLedgerException extends IllegalArgumentException {
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
        /** The current serial ledger cannot safely represent several calls. */
        MULTIPLE_FUNCTION_CALLS,
        /** The result references a different call than the output history. */
        CALL_ID_MISMATCH,
        /** An output item is outside the ledger's explicitly supported set. */
        UNKNOWN_OUTPUT_ITEM,
        /** The final replay response contains a non-final protocol item. */
        UNEXPECTED_FINAL_OUTPUT_ITEM
    }
}
