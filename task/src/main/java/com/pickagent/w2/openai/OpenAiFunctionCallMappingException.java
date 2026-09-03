package com.pickagent.w2.openai;

import java.util.Objects;

/**
 * Explicit failure while narrowing heterogeneous OpenAI output to the current single-call core contract.
 *
 * @author jamieLu
 * @since 2026-09-02
 */
public final class OpenAiFunctionCallMappingException extends IllegalArgumentException {
    /** Stable category for programmatic handling. */
    private final Reason reason;

    /**
     * Creates a mapping failure without a nested parser failure.
     *
     * @param reason stable failure category
     * @param message diagnostic message
     */
    public OpenAiFunctionCallMappingException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /**
     * Creates a mapping failure caused by malformed provider data.
     *
     * @param reason stable failure category
     * @param message diagnostic message
     * @param cause original parser failure
     */
    public OpenAiFunctionCallMappingException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /**
     * Returns the machine-readable failure category.
     *
     * @return mapping failure category
     */
    public Reason reason() {
        return reason;
    }

    /** Categories that callers can handle without parsing exception text. */
    public enum Reason {
        /** No function call was present in the output items. */
        NO_FUNCTION_CALL,
        /** More than one function call was present but the core supports one. */
        MULTIPLE_FUNCTION_CALLS,
        /** The arguments field was not valid JSON. */
        MALFORMED_ARGUMENTS_JSON,
        /** The parsed arguments root was not a JSON object. */
        ARGUMENTS_NOT_OBJECT,
        /** A JSON object member was not a string. */
        NON_STRING_ARGUMENT,
        /** A required call identifier or function name was blank. */
        INVALID_FUNCTION_CALL_FIELD
    }
}
