package com.pickagent.w1d5.infrastructure;

import com.pickagent.w1d5.core.ModelCommand;
import com.pickagent.w1d5.core.ModelGateway;
import com.pickagent.w1d5.core.ModelResult;

import java.util.Map;
import java.util.Objects;

/**
 * Deterministic, offline adapter for demos, tests, and local development.
 */
public final class ReplayModelGateway implements ModelGateway {
    private static final Map<Scenario, ModelResult> REPLAY_DATA = Map.of(
            Scenario.COMPLETED, new ModelResult.Completed(
                    "{\"name\":\"Architecture Review\",\"date\":\"2026-08-28\","
                            + "\"participants\":[\"Jamie\",\"Ada\"]}"
            ),
            Scenario.REFUSED, new ModelResult.Refused("replay safety refusal"),
            Scenario.INCOMPLETE, new ModelResult.Incomplete("max_output_tokens"),
            Scenario.TRANSPORT_FAILURE, new ModelResult.Failed(
                    ModelResult.FailureKind.TRANSPORT, "replay connection timeout"),
            Scenario.PROVIDER_FAILURE, new ModelResult.Failed(
                    ModelResult.FailureKind.PROVIDER, "replay provider rejected request"),
            Scenario.PROTOCOL_FAILURE, new ModelResult.Failed(
                    ModelResult.FailureKind.PROTOCOL, "replay response protocol mismatch")
    );

    private final Scenario scenario;

    public ReplayModelGateway(Scenario scenario) {
        this.scenario = Objects.requireNonNull(scenario, "scenario");
    }

    @Override
    public ModelResult generate(ModelCommand command) {
        Objects.requireNonNull(command, "command");
        return REPLAY_DATA.get(scenario);
    }

    public enum Scenario {
        COMPLETED,
        REFUSED,
        INCOMPLETE,
        TRANSPORT_FAILURE,
        PROVIDER_FAILURE,
        PROTOCOL_FAILURE
    }
}
