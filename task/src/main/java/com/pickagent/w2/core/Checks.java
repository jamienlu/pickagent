package com.pickagent.w2.core;

/**
 * W2 核心模型共用的轻量参数校验器。
 *
 * @author jamieLu
 * @since 2026-08-31
 */
final class Checks {
    /** 工具类不允许实例化。 */
    private Checks() {
    }

    /**
     * 校验字符串不为 {@code null}、空串或纯空白。
     *
     * @param value 待校验字符串
     * @param field 用于异常消息的字段名
     * @throws IllegalArgumentException value 为空时抛出
     */
    static void nonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be null or blank");
        }
    }
}
