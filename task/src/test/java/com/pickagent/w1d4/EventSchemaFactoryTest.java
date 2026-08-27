package com.pickagent.w1d4;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EventSchemaFactoryTest {

    @Test
    void requiresEveryDeclaredEventField() {
        Map<String, Object> schema = EventSchemaFactory.create();
        Map<String, Object> properties = asMap(schema.get("properties"));
        List<String> required = asStringList(schema.get("required"));

        assertEquals(Set.of("name", "date", "participants"), properties.keySet());
        assertEquals(Set.of("name", "date", "participants"), Set.copyOf(required));
    }

    @Test
    void participantsIsAnArrayOfStrings() {
        Map<String, Object> schema = EventSchemaFactory.create();
        Map<String, Object> properties = asMap(schema.get("properties"));
        Map<String, Object> participants = asMap(properties.get("participants"));
        Map<String, Object> items = asMap(participants.get("items"));

        assertEquals("array", participants.get("type"));
        assertEquals("string", items.get("type"));
    }

    @Test
    void rejectsAdditionalProperties() {
        Map<String, Object> schema = EventSchemaFactory.create();

        assertEquals("object", schema.get("type"));
        assertFalse((Boolean) schema.get("additionalProperties"));
    }

    @Test
    void convertsToOpenAiSdkSchemaOffline() {
        assertDoesNotThrow(EventSchemaFactory::createSdkSchema);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object value) {
        return (List<String>) value;
    }
}
