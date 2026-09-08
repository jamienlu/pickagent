package io.github.jamielu.agent.integration;

import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseOutputItem;
import io.github.jamielu.agent.api.AgentDecision;
import io.github.jamielu.agent.tool.ToolRegistry;
import io.github.jamielu.agent.api.ToolResult;
import io.github.jamielu.agent.example.support.ReplayOrderTool;
import io.github.jamielu.agent.openai.OpenAiFunctionCallMapper;
import io.github.jamielu.agent.openai.OpenAiFunctionCallOutputMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenAiToolBridgeContractTest {
    @Test
    void sdkCallRoundTripsThroughRegistryToSdkOutputWithOneExecution() throws Exception {
        ResponseFunctionToolCall inbound = ResponseFunctionToolCall.builder()
                .id("fc_order_001")
                .callId("call_order_001")
                .name("lookup_order")
                .arguments("{\"orderId\":\"ORD-001\"}")
                .build();
        AgentDecision.ToolCall coreCall = new OpenAiFunctionCallMapper()
                .map(List.of(ResponseOutputItem.ofFunctionCall(inbound)));
        AtomicInteger executions = new AtomicInteger();
        ReplayOrderTool replay = new ReplayOrderTool();
        ToolRegistry registry = new ToolRegistry(List.of(new ToolRegistry.Registration(
                ReplayOrderTool.DEFINITION,
                arguments -> {
                    executions.incrementAndGet();
                    return replay.execute(arguments);
                })));

        ToolResult result = registry.execute(coreCall);
        var outbound = new OpenAiFunctionCallOutputMapper().map(result);

        assertEquals(inbound.callId(), coreCall.callId());
        assertEquals(coreCall.callId(), result.callId());
        assertEquals(result.callId(), outbound.callId());
        assertEquals("Order ORD-001: SHIPPED", outbound.output().asString());
        assertEquals(1, executions.get());
    }
}


