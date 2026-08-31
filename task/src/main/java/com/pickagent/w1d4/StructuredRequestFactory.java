package com.pickagent.w1d4;

import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseTextConfig;

/**
 * 创建启用严格 Event JSON Schema 的 OpenAI Responses 请求参数。
 *
 * @author jamieLu
 * @since 2026-08-27
 */
public final class StructuredRequestFactory {
    /** Event Schema 在请求中的稳定名称。 */
    public static final String EVENT_SCHEMA_NAME = "event";

    private StructuredRequestFactory() {
    }

    /**
     * 创建严格结构化输出请求。
     *
     * @param model 模型标识
     * @param input 用户输入
     * @return 配置了严格 Event Schema 的 SDK 请求参数
     * @throws IllegalArgumentException model 或 input 为空时抛出
     */
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

    /**
     * 校验必填文本参数。
     *
     * @param value 参数值
     * @param fieldName 参数名称
     * @throws IllegalArgumentException 参数为空时抛出
     */
    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
    }
}
