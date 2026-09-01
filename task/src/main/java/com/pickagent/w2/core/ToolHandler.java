package com.pickagent.w2.core;

import java.util.Map;

/**
 * Execute one validated operation. Expected failures use ToolExecutionException;
 * unknown programming errors propagate. Never invoke the model or control the loop.
 */
@FunctionalInterface
public interface ToolHandler {
    String execute(Map<String, String> arguments) throws ToolExecutionException;
}
