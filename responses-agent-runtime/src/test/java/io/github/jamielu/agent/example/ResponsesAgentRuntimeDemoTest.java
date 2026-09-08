package io.github.jamielu.agent.example;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponsesAgentRuntimeDemoTest {
    @Test
    void printsObservablePassingProtocolProof() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        var evidence = ResponsesAgentRuntimeDemo.run(
                new PrintStream(bytes, true, StandardCharsets.UTF_8));
        String output = bytes.toString(StandardCharsets.UTF_8);

        assertTrue(output.contains("items.before=reasoning,function_call,function_call"));
        assertTrue(output.contains(
                "items.after=reasoning,function_call,function_call,function_call_output,function_call_output"));
        assertTrue(output.contains("call_ids.match=true"));
        assertTrue(output.contains("tool.executions=2"));
        assertTrue(output.contains("final.answer=ORD-001 is SHIPPED; ORD-404 was not found."));
        assertTrue(output.contains("ledger.proof=PASS"));
        assertEquals(2, evidence.toolExecutions());
        assertTrue(evidence.originalItemsPreserved());
    }
}


