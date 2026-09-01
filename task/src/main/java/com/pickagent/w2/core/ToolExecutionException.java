package com.pickagent.w2.core;

/**
 * 工具 adapter 明确分类的可预期执行失败。
 *
 * <p>该异常区别于参数校验拒绝和未知编程错误，由 Runtime 转换为 typed tool failure。</p>
 *
 * @author jamieLu
 * @since 2026-08-31
 */
public final class ToolExecutionException extends Exception {
    /**
     * 使用错误说明创建工具执行异常。
     *
     * @param message 非空白错误说明
     * @throws IllegalArgumentException message 为空时抛出
     */
    public ToolExecutionException(String message) {
        this(message, null);
    }

    /**
     * 使用错误说明和底层原因创建工具执行异常。
     *
     * @param message 非空白错误说明
     * @param cause 底层失败原因，可以为 {@code null}
     * @throws IllegalArgumentException message 为空时抛出
     */
    public ToolExecutionException(String message, Throwable cause) {
        super(message, cause);
        Checks.nonBlank(message, "tool failure message");
    }
}
