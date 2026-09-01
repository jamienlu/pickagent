package com.pickagent.w2.infrastructure;

import com.pickagent.w2.core.ToolDefinition;
import com.pickagent.w2.core.ToolHandler;

import java.util.Map;
import java.util.Set;

/** Local fixture lookup only; no clock, credentials, network, or external side effects. */
public final class ReplayOrderTool implements ToolHandler {
    public static final ToolDefinition DEFINITION = new ToolDefinition(
            "lookup_order", "Look up an order in offline fixture data", Set.of("orderId"));

    private static final Map<String, String> ORDERS = Map.of("ORD-001", "SHIPPED");

    @Override
    public String execute(Map<String, String> arguments) {
        String orderId = arguments.get("orderId");
        return "Order " + orderId + ": " + ORDERS.getOrDefault(orderId, "NOT_FOUND");
    }
}
