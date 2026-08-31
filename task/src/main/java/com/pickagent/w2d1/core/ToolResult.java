package com.pickagent.w2d1.core;

import java.util.Objects;

/** Tool output is data, associated with the original call; it is not a final answer. */
public record ToolResult(String callId, String output) {
    public ToolResult {
        Checks.nonBlank(callId, "callId");
        Objects.requireNonNull(output, "output");
    }
}
