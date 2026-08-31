package com.pickagent.w1d5.core;

/**
 * 应用核心发送给模型网关的最小供应商中立命令。
 *
 * @param prompt 已组装的模型提示
 * @author jamieLu
 * @since 2026-08-28
 */
public record ModelCommand(String prompt) {
    /** 校验模型提示必须包含有效文本。 */
    public ModelCommand {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt cannot be null or blank");
        }
    }
}
