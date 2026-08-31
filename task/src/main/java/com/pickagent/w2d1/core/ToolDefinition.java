package com.pickagent.w2d1.core;

import java.util.Set;

/** Small provider-neutral descriptor: all listed parameters are required non-blank strings. */
public record ToolDefinition(String name, String description, Set<String> requiredArguments) {
    public ToolDefinition {
        Checks.nonBlank(name, "tool name");
        Checks.nonBlank(description, "tool description");
        if (!name.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("tool name must match [a-z][a-z0-9_]*");
        }
        requiredArguments = Set.copyOf(requiredArguments);
        requiredArguments.forEach(argument -> Checks.nonBlank(argument, "argument name"));
    }
}
