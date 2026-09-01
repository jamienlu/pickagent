package com.pickagent.w2.core;

/** Runtime lifecycle; model adapters do not drive these transitions. */
public enum AgentState {
    START, MODEL, TOOL, FINAL, STOP
}
