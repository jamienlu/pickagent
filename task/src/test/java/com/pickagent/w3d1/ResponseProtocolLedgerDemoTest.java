package com.pickagent.w3d1;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseProtocolLedgerDemoTest {
    @Test
    void printsObservablePassingProtocolProof() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        var evidence = ResponseProtocolLedgerDemo.run(
                new PrintStream(bytes, true, StandardCharsets.UTF_8));
        String output = bytes.toString(StandardCharsets.UTF_8);

        assertTrue(output.contains("items.before=reasoning,function_call"));
        assertTrue(output.contains(
                "items.after=reasoning,function_call,function_call_output"));
        assertTrue(output.contains("call_id.match=true"));
        assertTrue(output.contains("tool.executions=1"));
        assertTrue(output.contains("final.answer=Order ORD-001 is SHIPPED."));
        assertTrue(output.contains("ledger.proof=PASS"));
        assertEquals(1, evidence.toolExecutions());
        assertTrue(evidence.originalItemsPreserved());
    }
}
