package com.pickagent.w2d3.openai;

import com.pickagent.w2.core.ToolResult;
import com.pickagent.w2.openai.OpenAiFunctionCallOutputMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiFunctionCallOutputMapperTest {
    private final OpenAiFunctionCallOutputMapper mapper = new OpenAiFunctionCallOutputMapper();

    @Test
    void mapsOriginalCallIdAndOutputText() {
        var output = mapper.map(new ToolResult("call_order_001", "Order ORD-001: SHIPPED"));

        assertEquals("call_order_001", output.callId());
        assertEquals("Order ORD-001: SHIPPED", output.output().asString());
    }

    @Test
    void preservesEmptyToolOutputAsAnEmptyString() {
        var output = mapper.map(new ToolResult("call_empty", ""));

        assertEquals("", output.output().asString());
    }

    @Test
    void nullToolResultIsRejectedAtTheMapperBoundary() {
        var failure = assertThrows(NullPointerException.class, () -> mapper.map(null));

        assertEquals("result", failure.getMessage());
    }
}
