package io.github.jamielu.agent.offline;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineResponsesDemoTest {
    // 场景：运行标准主源码目录中的离线入口；行为：执行完整确定性模型工具循环；预期：输出回答、精确消耗和账本通过证明。
    @Test
    void printsDeterministicAcceptanceEvidence() {
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            OfflineResponsesDemo.main(new String[0]);
        } finally {
            System.setOut(original);
        }

        String output = captured.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("final.answer=Replay answer: Order ORD-001: SHIPPED"));
        assertTrue(output.contains("model.calls=2"));
        assertTrue(output.contains("tool.calls=1"));
        assertTrue(output.contains("ledger.proof=PASS"));
    }
}
