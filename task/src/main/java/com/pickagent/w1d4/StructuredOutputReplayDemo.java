package com.pickagent.w1d4;

import java.util.List;

public final class StructuredOutputReplayDemo {
    private StructuredOutputReplayDemo() {
    }

    public static void main(String[] args) {
        StructuredResponseHandler handler = new StructuredResponseHandler();

        replay(handler, "completed + valid Event", StructuredResponseHandler.ResponseSnapshot.completed("""
                {"name":"AI Meetup","date":"2026-08-27","participants":["Alice","Bob"]}
                """));

        replay(handler, "refusal", StructuredResponseHandler.ResponseSnapshot.refusal(
                "The request cannot be fulfilled"));

        replay(handler, "incomplete / max_output_tokens", StructuredResponseHandler.ResponseSnapshot.incomplete(
                "max_output_tokens",
                "{\"name\":\"Partial Event\",\"date\":"));

        replay(handler, "completed + nonconforming JSON", StructuredResponseHandler.ResponseSnapshot.completed("""
                {"name":"AI Meetup","date":"2026-08-27","participants":[],"unexpected":true}
                """));
    }

    private static void replay(
            StructuredResponseHandler handler,
            String scenario,
            StructuredResponseHandler.ResponseSnapshot response
    ) {
        StructuredResponseHandler.HandlingResult result = handler.handle(response);
        System.out.println(scenario + " -> " + describe(result));
    }

    private static String describe(StructuredResponseHandler.HandlingResult result) {
        if (result instanceof StructuredResponseHandler.HandlingResult.EventAccepted accepted) {
            Event event = accepted.event();
            return "SUCCESS name=" + event.name()
                    + ", date=" + event.date()
                    + ", participants=" + List.copyOf(event.participants());
        }
        if (result instanceof StructuredResponseHandler.HandlingResult.Refused refused) {
            return "REFUSAL reason=" + refused.reason();
        }
        if (result instanceof StructuredResponseHandler.HandlingResult.Incomplete incomplete) {
            return "INCOMPLETE reason=" + incomplete.reason();
        }
        StructuredResponseHandler.HandlingResult.InvalidOutput invalid =
                (StructuredResponseHandler.HandlingResult.InvalidOutput) result;
        return "INVALID_OUTPUT code=" + invalid.failure().code()
                + ", reason=" + invalid.failure().reason();
    }
}
