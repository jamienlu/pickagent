package com.pickagent.w2d2;

import com.pickagent.w2.StrictToolContractDemo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StrictToolContractDemoTest {
    @Test
    void demoShowsMatchingContractCompletedReplayAndPreDispatchRejection() {
        var bytes = new ByteArrayOutputStream();

        StrictToolContractDemo.run(new PrintStream(bytes, true, StandardCharsets.UTF_8));

        String output = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("contract.match=true strict=true"), output);
        assertTrue(output.contains("valid.result=COMPLETED handlerExecutions=1 history=1"), output);
        assertTrue(output.contains("invalid.result=STOPPED reason=INVALID_ARGUMENTS"), output);
        assertTrue(output.contains("extra=[admin]"), output);
        assertTrue(output.contains("invalid.handlerExecutions=0"), output);
        assertTrue(output.contains("Strict schema does not replace Registry validation"), output);
    }
}
