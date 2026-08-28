package com.pickagent.w1d5.infrastructure;

import com.pickagent.w1d5.contract.ModelGatewayContractTest;
import com.pickagent.w1d5.core.ModelGateway;

class ReplayModelGatewayContractTest extends ModelGatewayContractTest {
    private static final String COMPLETED_CONTENT =
            "{\"name\":\"Architecture Review\",\"date\":\"2026-08-28\","
                    + "\"participants\":[\"Jamie\",\"Ada\"]}";

    @Override
    protected ModelGateway gatewayFor(ContractScenario scenario) {
        return new ReplayModelGateway(ReplayModelGateway.Scenario.valueOf(scenario.name()));
    }

    @Override
    protected String expectedCompletedContent() {
        return COMPLETED_CONTENT;
    }

    @Override
    protected String expectedRefusalReason() {
        return "replay safety refusal";
    }

    @Override
    protected String expectedIncompleteReason() {
        return "max_output_tokens";
    }

    @Override
    protected String expectedTransportFailureReason() {
        return "replay connection timeout";
    }

    @Override
    protected String expectedProviderFailureReason() {
        return "replay provider rejected request";
    }

    @Override
    protected String expectedProtocolFailureReason() {
        return "replay response protocol mismatch";
    }
}
