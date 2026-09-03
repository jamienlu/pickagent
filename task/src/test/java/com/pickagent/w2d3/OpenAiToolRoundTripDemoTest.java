package com.pickagent.w2d3;

import com.pickagent.w2.OpenAiToolRoundTripDemo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiToolRoundTripDemoTest {
    @Test
    void demoPrintsMatchingCallIdsOneExecutionAndSdkOutput() {
        var bytes = new ByteArrayOutputStream();

        OpenAiToolRoundTripDemo.run(new PrintStream(bytes, true, StandardCharsets.UTF_8));

        String output = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("inbound.sdk.call_id=call_order_001"), output);
        assertTrue(output.contains("core.toolCall.callId=call_order_001"), output);
        assertTrue(output.contains("core.toolResult.callId=call_order_001"), output);
        assertTrue(output.contains("outbound.sdk.call_id=call_order_001"), output);
        assertTrue(output.contains("outbound.sdk.output=Order ORD-001: SHIPPED"), output);
        assertTrue(output.contains("handlerExecutions=1"), output);
        assertTrue(output.contains("roundTrip.callIdMatch=true"), output);
    }
}
