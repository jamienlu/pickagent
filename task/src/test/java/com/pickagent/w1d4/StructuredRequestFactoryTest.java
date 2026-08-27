package com.pickagent.w1d4;

import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseFormatTextConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredRequestFactoryTest {

    @Test
    void preservesRequestedModel() {
        ResponseCreateParams params = createParams();

        assertEquals("gpt-4o-mini", params.model().orElseThrow().asString());
    }

    @Test
    void preservesTextInput() {
        ResponseCreateParams params = createParams();

        assertEquals("Extract event information.", params.input().orElseThrow().asText());
    }

    @Test
    void configuresJsonSchemaFormatAndName() {
        ResponseFormatTextConfig format = paramsFormat(createParams());

        assertTrue(format.isJsonSchema());
        assertEquals(StructuredRequestFactory.EVENT_SCHEMA_NAME, format.asJsonSchema().name());
    }

    @Test
    void enablesStrictSchemaAdherence() {
        ResponseFormatTextJsonSchemaConfig jsonSchema = paramsFormat(createParams()).asJsonSchema();

        assertEquals(true, jsonSchema.strict().orElseThrow());
    }

    @Test
    void reusesNonEmptyEventSchema() {
        ResponseFormatTextJsonSchemaConfig.Schema schema =
                paramsFormat(createParams()).asJsonSchema().schema();

        assertFalse(schema._additionalProperties().isEmpty());
        assertEquals(EventSchemaFactory.createSdkSchema(), schema);
    }

    @Test
    void rejectsBlankModelBeforeBuildingRequest() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> StructuredRequestFactory.create(" ", "Extract event information."));

        assertTrue(exception.getMessage().contains("model"));
    }

    @Test
    void rejectsBlankInputBeforeBuildingRequest() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> StructuredRequestFactory.create("gpt-4o-mini", "\n\t"));

        assertTrue(exception.getMessage().contains("input"));
    }

    private static ResponseCreateParams createParams() {
        return StructuredRequestFactory.create("gpt-4o-mini", "Extract event information.");
    }

    private static ResponseFormatTextConfig paramsFormat(ResponseCreateParams params) {
        return params.text().orElseThrow().format().orElseThrow();
    }
}
