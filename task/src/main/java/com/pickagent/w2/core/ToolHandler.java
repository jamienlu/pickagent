package com.pickagent.w2.core;

import java.util.Map;

/**
 * 执行一次已经通过 Registry 校验的工具操作。
 *
 * <p>可预期执行失败使用 {@link ToolExecutionException}；未知编程错误继续向上抛出。
 * 实现不得调用模型或控制 Agent 循环。</p>
 *
 * @author jamieLu
 * @since 2026-08-31
 */
@FunctionalInterface
public interface ToolHandler {
    /**
     * 使用已验证参数执行工具操作。
     *
     * @param arguments 与工具契约完全匹配的参数
     * @return 提交给模型的工具结果文本，不允许返回 {@code null}
     * @throws ToolExecutionException 已识别的工具执行失败
     */
    String execute(Map<String, String> arguments) throws ToolExecutionException;
}
