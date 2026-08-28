package com.pickagent.w1d5.core;

public record ModelCommand(String prompt) {
    public ModelCommand {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt cannot be null or blank");
        }
    }
}
