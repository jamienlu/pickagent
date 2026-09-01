package com.pickagent.w2.infrastructure;

import com.pickagent.w2.core.ToolDefinition;
import com.pickagent.w2.core.ToolHandler;

import java.util.Map;
import java.util.Set;

/**
 * 基于本地固定数据的订单查询工具 adapter。
 *
 * <p>实现不使用时钟、凭据、网络或外部副作用，仅用于可重复离线验证。</p>
 *
 * @author jamieLu
 * @since 2026-08-31
 */
public final class ReplayOrderTool implements ToolHandler {
    /** 对外公开的供应商中立订单查询工具契约。 */
    public static final ToolDefinition DEFINITION = new ToolDefinition(
            "lookup_order", "Look up an order in offline fixture data", Set.of("orderId"));

    /** 固定订单状态数据。 */
    private static final Map<String, String> ORDERS = Map.of("ORD-001", "SHIPPED");

    /** 创建本地固定数据订单查询工具。 */
    public ReplayOrderTool() {
    }

    /**
     * 查询固定订单数据。
     *
     * @param arguments 已由 Registry 校验且包含 orderId 的参数
     * @return 订单状态文本；订单不存在时返回 NOT_FOUND
     */
    @Override
    public String execute(Map<String, String> arguments) {
        String orderId = arguments.get("orderId");
        return "Order " + orderId + ": " + ORDERS.getOrDefault(orderId, "NOT_FOUND");
    }
}
