package com.pickagent.w2.openai;

import com.openai.core.JsonValue;
import com.openai.models.responses.FunctionTool;
import com.pickagent.w2.core.ToolDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 将供应商中立工具定义映射为 OpenAI Responses {@link FunctionTool} 的 adapter。
 *
 * <p>映射结果显式启用 Strict mode，将全部参数声明为 required string，
 * 并关闭额外属性；核心包不依赖 OpenAI SDK。</p>
 *
 * @author jamieLu
 * @since 2026-09-01
 */
public final class OpenAiFunctionToolMapper {
    /** 创建 OpenAI 函数工具映射器。 */
    public OpenAiFunctionToolMapper() {
    }

    /**
     * 将核心工具定义转换为严格 OpenAI FunctionTool。
     *
     * @param definition 供应商中立工具定义
     * @return strict=true 的 OpenAI FunctionTool
     * @throws NullPointerException definition 为 {@code null} 时抛出
     */
    public FunctionTool map(ToolDefinition definition) {
        Objects.requireNonNull(definition, "definition");

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (ToolDefinition.RequiredStringParameter parameter : definition.parameters()) {
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("type", "string");
            if (!parameter.description().isBlank()) {
                property.put("description", parameter.description());
            }
            properties.put(parameter.name(), property);
            required.add(parameter.name());
        }

        FunctionTool.Parameters parameters = FunctionTool.Parameters.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(properties))
                .putAdditionalProperty("required", JsonValue.from(List.copyOf(required)))
                .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                .build();

        return FunctionTool.builder()
                .name(definition.name())
                .description(definition.description())
                .parameters(parameters)
                .strict(true)
                .build();
    }
}
