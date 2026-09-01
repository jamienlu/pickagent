package com.pickagent.w2.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * 工具白名单、参数校验、分发以及原始 callId 关联注册表。
 *
 * <p>Registry 是工具执行前的本地可信边界；模型生成的参数即使符合供应商 Schema，
 * 仍必须经过此处校验。</p>
 *
 * @author jamieLu
 * @since 2026-08-31
 */
public final class ToolRegistry {
    /** 按工具名称索引的不可修改注册表。 */
    private final Map<String, Registration> registrations;

    /**
     * 创建工具注册表。
     *
     * @param registrations 工具定义与 handler 注册列表
     * @throws IllegalArgumentException 出现重复工具名称时抛出
     * @throws NullPointerException registrations 或其中元素为 {@code null} 时抛出
     */
    public ToolRegistry(List<Registration> registrations) {
        Map<String, Registration> byName = new LinkedHashMap<>();
        for (Registration registration : List.copyOf(registrations)) {
            String name = registration.definition().name();
            if (byName.putIfAbsent(name, registration) != null) {
                throw new IllegalArgumentException("duplicate tool registration: " + name);
            }
        }
        this.registrations = Collections.unmodifiableMap(byName);
    }

    /**
     * 返回当前允许向模型公开的工具定义。
     *
     * @return 按注册顺序排列的不可修改定义列表
     */
    public List<ToolDefinition> definitions() {
        return registrations.values().stream().map(Registration::definition).toList();
    }

    /**
     * 校验并执行一次工具调用。
     *
     * <p>该方法拒绝未知工具、缺失/多余参数和空白参数；handler 只能返回输出文本，
     * 结果 callId 始终复制自原始调用。</p>
     *
     * @param call 模型提出的工具调用
     * @return 与原调用关联的工具结果
     * @throws RejectedCall 工具未知或参数不符合契约时抛出
     * @throws ToolExecutionException handler 报告可预期执行失败时抛出
     * @throws NullPointerException call 或 handler 输出为 {@code null} 时抛出
     */
    public ToolResult execute(AgentDecision.ToolCall call) throws ToolExecutionException {
        Objects.requireNonNull(call, "call");
        Registration registration = registrations.get(call.toolName());
        if (registration == null) {
            throw new RejectedCall(Rejection.UNKNOWN_TOOL, "unknown tool: " + call.toolName());
        }
        var required = registration.definition().requiredArguments();
        var actual = call.arguments().keySet();
        if (!actual.equals(required)) {
            var missing = new TreeSet<>(required);
            missing.removeAll(actual);
            var extra = new TreeSet<>(actual);
            extra.removeAll(required);
            throw new RejectedCall(Rejection.INVALID_ARGUMENTS,
                    "invalid arguments for " + call.toolName() + ": missing=" + missing + ", extra=" + extra);
        }
        for (String key : new TreeSet<>(required)) {
            if (call.arguments().get(key).isBlank()) {
                throw new RejectedCall(Rejection.INVALID_ARGUMENTS, "blank argument: " + key);
            }
        }
        // The handler cannot rewrite callId. Unknown handler bugs intentionally propagate.
        String output = Objects.requireNonNull(
                registration.handler().execute(call.arguments()), "tool handler returned null");
        return new ToolResult(call.callId(), output);
    }

    /**
     * 一项工具定义及其执行器注册。
     *
     * @param definition 供应商中立工具定义
     * @param handler 工具执行器
     */
    public record Registration(ToolDefinition definition, ToolHandler handler) {
        /**
         * 创建工具注册项。
         *
         * @throws NullPointerException definition 或 handler 为 {@code null} 时抛出
         */
        public Registration {
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(handler, "handler");
        }
    }

    /** Registry 在分发前拒绝工具调用的原因。 */
    public enum Rejection {
        /** 工具名称未在白名单中注册。 */
        UNKNOWN_TOOL,
        /** 参数字段集合或值违反工具契约。 */
        INVALID_ARGUMENTS
    }

    /**
     * Registry 明确识别的调用拒绝异常。
     *
     * <p>只有该类预期校验失败会被 Runtime 归一化为停止结果，未知编程错误继续暴露。</p>
     */
    public static final class RejectedCall extends RuntimeException {
        /** 拒绝分类。 */
        private final Rejection reason;

        /**
         * 创建 Registry 拒绝异常。
         *
         * @param reason 拒绝分类
         * @param message 拒绝详情
         */
        private RejectedCall(Rejection reason, String message) {
            super(message);
            this.reason = reason;
        }

        /**
         * 返回拒绝分类。
         *
         * @return 拒绝原因
         */
        public Rejection reason() {
            return reason;
        }
    }
}
