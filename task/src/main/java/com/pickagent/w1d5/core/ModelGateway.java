package com.pickagent.w1d5.core;

@FunctionalInterface
public interface ModelGateway {
    ModelResult generate(ModelCommand command);
}
