package com.pickagent.w1d4;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredOutputDecoderTest {
    private final StructuredOutputDecoder decoder = new StructuredOutputDecoder();

    @Test
    void decodesValidEventObject() {
        DecodeResult result = decoder.decode("""
                {"name":"AI 分享会","date":"2026-08-27","participants":["Alice","小明"]}
                """);

        DecodeResult.Success success = assertInstanceOf(DecodeResult.Success.class, result);
        assertEquals(new Event("AI 分享会", "2026-08-27", List.of("Alice", "小明")), success.event());
    }

    @Test
    void reportsMissingRequiredField() {
        DecodeResult.Failure failure = failureOf(
                decoder.decode("{\"name\":\"AI 分享会\",\"participants\":[\"Alice\"]}"));

        assertEquals(DecodeResult.ErrorCode.MISSING_FIELDS, failure.code());
        assertTrue(failure.reason().contains("date"));
    }

    @Test
    void reportsAdditionalField() {
        DecodeResult.Failure failure = failureOf(decoder.decode("""
                {"name":"AI 分享会","date":"2026-08-27","participants":[],"location":"Shanghai"}
                """));

        assertEquals(DecodeResult.ErrorCode.EXTRA_FIELDS, failure.code());
        assertTrue(failure.reason().contains("location"));
    }

    @Test
    void reportsWrongFieldType() {
        DecodeResult.Failure failure = failureOf(decoder.decode("""
                {"name":"AI 分享会","date":"2026-08-27","participants":"Alice"}
                """));

        assertEquals(DecodeResult.ErrorCode.WRONG_FIELD_TYPE, failure.code());
        assertTrue(failure.reason().contains("participants"));
        assertTrue(failure.reason().contains("array"));
    }

    @Test
    void reportsWrongArrayElementType() {
        DecodeResult.Failure failure = failureOf(decoder.decode("""
                {"name":"AI 分享会","date":"2026-08-27","participants":["Alice",42]}
                """));

        assertEquals(DecodeResult.ErrorCode.WRONG_FIELD_TYPE, failure.code());
        assertTrue(failure.reason().contains("participants[1]"));
        assertTrue(failure.reason().contains("string"));
    }

    @Test
    void reportsMalformedJson() {
        DecodeResult.Failure failure = failureOf(
                decoder.decode("{\"name\":\"AI 分享会\",\"date\":}"));

        assertEquals(DecodeResult.ErrorCode.MALFORMED_JSON, failure.code());
        assertTrue(failure.reason().contains("not valid JSON"));
    }

    @Test
    void reportsBlankText() {
        DecodeResult.Failure failure = failureOf(decoder.decode("   \n\t"));

        assertEquals(DecodeResult.ErrorCode.EMPTY_TEXT, failure.code());
        assertTrue(failure.reason().contains("empty"));
    }

    @Test
    void reportsNullTextWithoutThrowingNullPointerException() {
        DecodeResult.Failure failure = failureOf(decoder.decode(null));

        assertEquals(DecodeResult.ErrorCode.EMPTY_TEXT, failure.code());
        assertTrue(failure.reason().contains("null"));
    }

    @Test
    void reportsNonObjectJson() {
        DecodeResult.Failure failure = failureOf(decoder.decode("[]"));

        assertEquals(DecodeResult.ErrorCode.NOT_JSON_OBJECT, failure.code());
        assertTrue(failure.reason().contains("array"));
    }

    private static DecodeResult.Failure failureOf(DecodeResult result) {
        return assertInstanceOf(DecodeResult.Failure.class, result);
    }
}
