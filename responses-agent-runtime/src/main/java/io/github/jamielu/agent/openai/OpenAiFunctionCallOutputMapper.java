package io.github.jamielu.agent.openai;

import com.openai.models.responses.ResponseInputItem;
import io.github.jamielu.agent.api.ToolResult;

import java.util.Objects;

/** 把供应商中立工具结果映射为 OpenAI Responses 函数调用输出输入条目。 */
public final class OpenAiFunctionCallOutputMapper {
    /** 创建无状态出站映射器。 */
    public OpenAiFunctionCallOutputMapper() {
    }

    /**
     * 保留原始调用标识和输出文本。
     *
     * @param result 供应商中立工具结果
     * @return SDK 函数调用输出条目
     */
    public ResponseInputItem.FunctionCallOutput map(ToolResult result) {
        Objects.requireNonNull(result, "result");
        return ResponseInputItem.FunctionCallOutput.builder()
                .callId(result.callId())
                .output(result.output())
                .build();
    }
}


