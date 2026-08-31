package com.pickagent.w2d1.core;

/** Expected tool-operation failure, deliberately classified by the tool adapter. */
public final class ToolExecutionException extends Exception {
    public ToolExecutionException(String message) {
        this(message, null);
    }

    public ToolExecutionException(String message, Throwable cause) {
        super(message, cause);
        Checks.nonBlank(message, "tool failure message");
    }
}
