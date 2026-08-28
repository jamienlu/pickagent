package com.pickagent.w1d5.core;

import java.util.Objects;

final class FakeModelGateway implements ModelGateway {
    private ModelResult nextResult;
    private ModelCommand lastCommand;
    private int callCount;

    FakeModelGateway(ModelResult nextResult) {
        this.nextResult = Objects.requireNonNull(nextResult, "nextResult");
    }

    @Override
    public ModelResult generate(ModelCommand command) {
        lastCommand = Objects.requireNonNull(command, "command");
        callCount++;
        return nextResult;
    }

    ModelCommand lastCommand() {
        return lastCommand;
    }

    int callCount() {
        return callCount;
    }
}
