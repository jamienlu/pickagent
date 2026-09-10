package io.github.jamielu.agent.openai;

import io.github.jamielu.agent.api.ToolResult;
import io.github.jamielu.agent.openai.OpenAiFunctionCallOutputMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiFunctionCallOutputMapperTest {
    private final OpenAiFunctionCallOutputMapper mapper = new OpenAiFunctionCallOutputMapper();

    // 场景：工具结果映射保留原始调用标识和输出文本；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void mapsOriginalCallIdAndOutputText() {
        var output = mapper.map(new ToolResult("call_order_001", "Order ORD-001: SHIPPED"));

        assertEquals("call_order_001", output.callId());
        assertEquals("Order ORD-001: SHIPPED", output.output().asString());
    }

    // 场景：空工具输出保持为空字符串；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void preservesEmptyToolOutputAsAnEmptyString() {
        var output = mapper.map(new ToolResult("call_empty", ""));

        assertEquals("", output.output().asString());
    }

    // 场景：映射边界拒绝空工具结果；行为：执行对应代码路径；预期：相关业务断言全部成立。
    @Test
    void nullToolResultIsRejectedAtTheMapperBoundary() {
        var failure = assertThrows(NullPointerException.class, () -> mapper.map(null));

        assertEquals("result", failure.getMessage());
    }
}


