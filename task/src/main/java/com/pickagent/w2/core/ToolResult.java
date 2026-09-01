package com.pickagent.w2.core;

import java.util.Objects;

/**
 * 与原工具调用关联的工具执行结果。
 *
 * <p>工具输出是供下一次模型决策使用的数据，不代表面向用户的最终回答。</p>
 *
 * @param callId 原工具调用标识
 * @param output 工具输出文本
 * @author jamieLu
 * @since 2026-08-31
 */
public record ToolResult(String callId, String output) {
    /**
     * 创建工具结果并校验关联标识和输出。
     *
     * @throws IllegalArgumentException callId 为空时抛出
     * @throws NullPointerException output 为 {@code null} 时抛出
     */
    public ToolResult {
        Checks.nonBlank(callId, "callId");
        Objects.requireNonNull(output, "output");
    }
}
