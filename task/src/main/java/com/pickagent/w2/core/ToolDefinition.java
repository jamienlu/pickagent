package com.pickagent.w2.core;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Small provider-neutral descriptor: every parameter is a required non-blank string. */
public record ToolDefinition(String name, String description, List<RequiredStringParameter> parameters) {
    public ToolDefinition {
        Checks.nonBlank(name, "tool name");
        Checks.nonBlank(description, "tool description");
        if (!name.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("tool name must match [a-z][a-z0-9_]*");
        }
        parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        Set<String> names = new LinkedHashSet<>();
        for (RequiredStringParameter parameter : parameters) {
            Objects.requireNonNull(parameter, "parameter");
            if (!names.add(parameter.name())) {
                throw new IllegalArgumentException("duplicate parameter: " + parameter.name());
            }
        }
    }

    /**
     * Compatible constructor for the original name-only representation.
     * Set input is sorted so generated schemas are deterministic.
     */
    public ToolDefinition(String name, String description, Set<String> requiredArguments) {
        this(name, description, toParameters(requiredArguments));
    }

    /** Compatible accessor used by the existing runtime validator. */
    public Set<String> requiredArguments() {
        Set<String> names = new LinkedHashSet<>();
        parameters.forEach(parameter -> names.add(parameter.name()));
        return Collections.unmodifiableSet(names);
    }

    private static List<RequiredStringParameter> toParameters(Set<String> requiredArguments) {
        Objects.requireNonNull(requiredArguments, "requiredArguments");
        return requiredArguments.stream()
                .sorted()
                .map(RequiredStringParameter::new)
                .toList();
    }

    /** No optional, array, object, or non-string variants are admitted by this minimal contract. */
    public record RequiredStringParameter(String name, String description) {
        public RequiredStringParameter {
            Checks.nonBlank(name, "argument name");
            description = Objects.requireNonNull(description, "parameter description");
        }

        public RequiredStringParameter(String name) {
            this(name, "");
        }
    }
}
