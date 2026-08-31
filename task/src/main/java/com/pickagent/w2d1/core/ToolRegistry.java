package com.pickagent.w2d1.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/** Allowlist, argument validation, dispatch, and original callId association. */
public final class ToolRegistry {
    private final Map<String, Registration> registrations;

    public ToolRegistry(List<Registration> registrations) {
        Map<String, Registration> byName = new LinkedHashMap<>();
        for (Registration registration : List.copyOf(registrations)) {
            String name = registration.definition().name();
            if (byName.putIfAbsent(name, registration) != null) {
                throw new IllegalArgumentException("duplicate tool registration: " + name);
            }
        }
        this.registrations = Collections.unmodifiableMap(byName);
    }

    public List<ToolDefinition> definitions() {
        return registrations.values().stream().map(Registration::definition).toList();
    }

    public ToolResult execute(AgentDecision.ToolCall call) {
        Objects.requireNonNull(call, "call");
        Registration registration = registrations.get(call.toolName());
        if (registration == null) {
            throw new RejectedCall(Rejection.UNKNOWN_TOOL, "unknown tool: " + call.toolName());
        }
        var required = registration.definition().requiredArguments();
        var actual = call.arguments().keySet();
        if (!actual.equals(required)) {
            var missing = new TreeSet<>(required);
            missing.removeAll(actual);
            var extra = new TreeSet<>(actual);
            extra.removeAll(required);
            throw new RejectedCall(Rejection.INVALID_ARGUMENTS,
                    "invalid arguments for " + call.toolName() + ": missing=" + missing + ", extra=" + extra);
        }
        for (String key : new TreeSet<>(required)) {
            if (call.arguments().get(key).isBlank()) {
                throw new RejectedCall(Rejection.INVALID_ARGUMENTS, "blank argument: " + key);
            }
        }
        // The handler cannot rewrite callId. Unknown handler bugs intentionally propagate.
        String output = Objects.requireNonNull(
                registration.handler().execute(call.arguments()), "tool handler returned null");
        return new ToolResult(call.callId(), output);
    }

    public record Registration(ToolDefinition definition, ToolHandler handler) {
        public Registration {
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(handler, "handler");
        }
    }

    public enum Rejection {
        UNKNOWN_TOOL, INVALID_ARGUMENTS
    }

    /** Only expected registry validation failures are normalized by the Runtime. */
    public static final class RejectedCall extends RuntimeException {
        private final Rejection reason;

        private RejectedCall(Rejection reason, String message) {
            super(message);
            this.reason = reason;
        }

        public Rejection reason() {
            return reason;
        }
    }
}
