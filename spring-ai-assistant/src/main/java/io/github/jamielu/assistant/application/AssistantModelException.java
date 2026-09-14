package io.github.jamielu.assistant.application;

/** Wraps a model invocation failure at the application boundary. */
public final class AssistantModelException extends RuntimeException {
    /**
     * Creates a model failure while retaining the provider exception as cause.
     *
     * @param cause underlying model failure
     */
    public AssistantModelException(Throwable cause) {
        super("assistant model invocation failed", cause);
    }

    /** Creates a model failure for an invalid model response. */
    public AssistantModelException(String message) {
        super(message);
    }
}
