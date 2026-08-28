package com.pickagent.w1d5.core;

import java.util.Objects;

public final class GenerateEventUseCase {
    private static final String PROMPT_PREFIX =
            "Extract one event and return its name, date, and participants: ";

    private final ModelGateway modelGateway;
    private final EventParser eventParser;

    public GenerateEventUseCase(ModelGateway modelGateway, EventParser eventParser) {
        this.modelGateway = Objects.requireNonNull(modelGateway, "modelGateway");
        this.eventParser = Objects.requireNonNull(eventParser, "eventParser");
    }

    public GenerateEventResult generate(String eventDescription) {
        ModelCommand command = new ModelCommand(PROMPT_PREFIX + requireDescription(eventDescription));

        ModelResult modelResult = Objects.requireNonNull(
                modelGateway.generate(command),
                "modelGateway returned null"
        );

        if (modelResult instanceof ModelResult.Refused refused) {
            return new GenerateEventResult.Refused(refused.reason());
        }
        if (modelResult instanceof ModelResult.Incomplete incomplete) {
            return new GenerateEventResult.Incomplete(incomplete.reason());
        }
        if (modelResult instanceof ModelResult.Failed failed) {
            return new GenerateEventResult.Failed(mapFailureKind(failed.kind()), failed.reason());
        }

        ModelResult.Completed completed = (ModelResult.Completed) modelResult;
        EventParser.ParseResult parseResult = Objects.requireNonNull(
                eventParser.parse(completed.content()),
                "eventParser returned null"
        );
        if (parseResult instanceof EventParser.ParseResult.Parsed parsed) {
            return new GenerateEventResult.Generated(parsed.event());
        }

        EventParser.ParseResult.Invalid invalid = (EventParser.ParseResult.Invalid) parseResult;
        return new GenerateEventResult.Failed(
                GenerateEventResult.FailureKind.INVALID_MODEL_OUTPUT,
                invalid.reason()
        );
    }

    private static String requireDescription(String eventDescription) {
        if (eventDescription == null || eventDescription.isBlank()) {
            throw new IllegalArgumentException("eventDescription cannot be null or blank");
        }
        return eventDescription;
    }

    private static GenerateEventResult.FailureKind mapFailureKind(ModelResult.FailureKind kind) {
        return switch (kind) {
            case TRANSPORT -> GenerateEventResult.FailureKind.GATEWAY_TRANSPORT;
            case PROVIDER -> GenerateEventResult.FailureKind.GATEWAY_PROVIDER;
            case PROTOCOL -> GenerateEventResult.FailureKind.GATEWAY_PROTOCOL;
        };
    }
}
