package com.pickagent.w2;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentRuntimeDemoTest {
    @Test
    void demoPrintsCompleteDeterministicStepTrace() {
        var bytes = new ByteArrayOutputStream();
        try (var out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            AgentRuntimeDemo.run(out);
        }

        assertEquals("""
                step=1 decision=ToolCall
                  tool call: callId=call_order_001, tool=lookup_order, arguments={orderId=ORD-001}
                  observation: callId=call_order_001, output=Order ORD-001: SHIPPED
                step=2 decision=FinalAnswer
                  final answer: Replay answer: Order ORD-001: SHIPPED
                TRACE: START -> MODEL -> TOOL -> MODEL -> FINAL -> STOP
                result=Completed steps=2
                """, bytes.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"));
    }
}
