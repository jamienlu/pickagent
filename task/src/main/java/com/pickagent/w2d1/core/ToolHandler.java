package com.pickagent.w2d1.core;

import java.util.Map;

/** Execute exactly one validated tool operation; never invoke the model or control the loop. */
@FunctionalInterface
public interface ToolHandler {
    String execute(Map<String, String> arguments);
}
