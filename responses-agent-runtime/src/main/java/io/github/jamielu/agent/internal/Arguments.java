package io.github.jamielu.agent.internal;

/** 公共运行契约共用的参数校验辅助类。 */
public final class Arguments {
    /** 工具类不允许实例化。 */
    private Arguments() {
    }

    /**
     * 校验字符串不为 {@code null}、空串或纯空白。
     *
     * @param value 待校验字符串
     * @param field 用于异常消息的字段名
     * @throws IllegalArgumentException value 为空时抛出
     */
    public static void nonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be null or blank");
        }
    }
}



