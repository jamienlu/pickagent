package com.pickagent.w2.core;

/** One invocation only: implementations translate model I/O but never execute tools or loop. */
@FunctionalInterface
public interface AgentModelPort {
    AgentDecision decide(AgentContext context);
}
