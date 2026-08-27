package com.pickagent.w1d4;

import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseTextConfig;

public final class StructuredRequestFactory {
    public static final String EVENT_SCHEMA_NAME = "event";

    private StructuredRequestFactory() {
    }

    public static ResponseCreateParams create(String model, String input) {
        requireText(model, "model");
        requireText(input, "input");

        ResponseFormatTextJsonSchemaConfig format =
                ResponseFormatTextJsonSchemaConfig.builder()
                        .name(EVENT_SCHEMA_NAME)
                        .strict(true)
                        .schema(EventSchemaFactory.createSdkSchema())
                        .build();

        return ResponseCreateParams.builder()
                .model(model)
                .input(input)
                .text(ResponseTextConfig.builder().format(format).build())
                .build();
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
    }
}
