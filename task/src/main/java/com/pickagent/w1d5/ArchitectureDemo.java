package com.pickagent.w1d5;

import com.pickagent.w1d5.core.GenerateEventResult;
import com.pickagent.w1d5.core.GenerateEventUseCase;
import com.pickagent.w1d5.infrastructure.ReplayModelGateway;
import com.pickagent.w1d5.infrastructure.StructuredEventParser;

public final class ArchitectureDemo {
    private ArchitectureDemo() {
    }

    public static void main(String[] args) {
        demonstrate(ReplayModelGateway.Scenario.COMPLETED);
        demonstrate(ReplayModelGateway.Scenario.REFUSED);
        demonstrate(ReplayModelGateway.Scenario.INCOMPLETE);
    }

    private static void demonstrate(ReplayModelGateway.Scenario scenario) {
        var gateway = new ReplayModelGateway(scenario);
        var useCase = new GenerateEventUseCase(gateway, new StructuredEventParser());
        GenerateEventResult result = useCase.generate("Prepare the replay architecture review event.");

        System.out.println(scenario.name().toLowerCase() + " -> " + describe(result));
    }

    private static String describe(GenerateEventResult result) {
        if (result instanceof GenerateEventResult.Generated generated) {
            return "generated: " + generated.event();
        }
        if (result instanceof GenerateEventResult.Refused refused) {
            return "refused: " + refused.reason();
        }
        if (result instanceof GenerateEventResult.Incomplete incomplete) {
            return "incomplete: " + incomplete.reason();
        }

        GenerateEventResult.Failed failed = (GenerateEventResult.Failed) result;
        return "failed[" + failed.kind() + "]: " + failed.reason();
    }
}
