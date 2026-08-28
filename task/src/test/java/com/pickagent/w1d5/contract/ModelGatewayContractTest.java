package com.pickagent.w1d5.contract;

import com.pickagent.w1d5.core.ModelCommand;
import com.pickagent.w1d5.core.ModelGateway;
import com.pickagent.w1d5.core.ModelResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Adapter authors extend this class and provide deterministic fixtures for each scenario.
 */
public abstract class ModelGatewayContractTest {
    protected abstract ModelGateway gatewayFor(ContractScenario scenario);

    @Test
    void completedPreservesFinalContent() {
        ModelResult.Completed completed = assertInstanceOf(
                ModelResult.Completed.class,
                generate(ContractScenario.COMPLETED)
        );
        assertEquals(expectedCompletedContent(), completed.content());
    }

    @Test
    void refusalRemainsDistinctAndPreservesReason() {
        ModelResult.Refused refused = assertInstanceOf(
                ModelResult.Refused.class,
                generate(ContractScenario.REFUSED)
        );
        assertEquals(expectedRefusalReason(), refused.reason());
    }

    @Test
    void incompleteRemainsDistinctAndPreservesReason() {
        ModelResult.Incomplete incomplete = assertInstanceOf(
                ModelResult.Incomplete.class,
                generate(ContractScenario.INCOMPLETE)
        );
        assertEquals(expectedIncompleteReason(), incomplete.reason());
    }

    @Test
    void transportFailurePreservesFailureKindAndReason() {
        assertFailure(
                ContractScenario.TRANSPORT_FAILURE,
                ModelResult.FailureKind.TRANSPORT,
                expectedTransportFailureReason()
        );
    }

    @Test
    void providerFailurePreservesFailureKindAndReason() {
        assertFailure(
                ContractScenario.PROVIDER_FAILURE,
                ModelResult.FailureKind.PROVIDER,
                expectedProviderFailureReason()
        );
    }

    @Test
    void protocolFailurePreservesFailureKindAndReason() {
        assertFailure(
                ContractScenario.PROTOCOL_FAILURE,
                ModelResult.FailureKind.PROTOCOL,
                expectedProtocolFailureReason()
        );
    }

    protected abstract String expectedCompletedContent();

    protected abstract String expectedRefusalReason();

    protected abstract String expectedIncompleteReason();

    protected abstract String expectedTransportFailureReason();

    protected abstract String expectedProviderFailureReason();

    protected abstract String expectedProtocolFailureReason();

    private ModelResult generate(ContractScenario scenario) {
        return gatewayFor(scenario).generate(new ModelCommand("contract-test prompt"));
    }

    private void assertFailure(
            ContractScenario scenario,
            ModelResult.FailureKind expectedKind,
            String expectedReason
    ) {
        ModelResult.Failed failed = assertInstanceOf(
                ModelResult.Failed.class,
                generate(scenario)
        );
        assertEquals(expectedKind, failed.kind());
        assertEquals(expectedReason, failed.reason());
    }

    protected enum ContractScenario {
        COMPLETED,
        REFUSED,
        INCOMPLETE,
        TRANSPORT_FAILURE,
        PROVIDER_FAILURE,
        PROTOCOL_FAILURE
    }
}
