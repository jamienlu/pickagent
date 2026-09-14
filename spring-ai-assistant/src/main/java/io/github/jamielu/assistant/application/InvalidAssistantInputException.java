package io.github.jamielu.assistant.application;

/** Signals that the caller did not supply usable user content. */
public final class InvalidAssistantInputException extends RuntimeException {
    /** Creates an invalid-input failure with a stable diagnostic message. */
    public InvalidAssistantInputException() {
        super("message must not be blank");
    }
}
