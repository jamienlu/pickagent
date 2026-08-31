package com.pickagent.w1d5.core;

import java.util.Objects;

/**
 * 将模型文本转换为应用核心事件的出站解析端口。
 *
 * @author jamieLu
 * @since 2026-08-28
 */
@FunctionalInterface
public interface EventParser {
    /**
     * 解析并验证模型返回的事件文本。
     *
     * @param content 模型完成文本
     * @return 解析成功事件或稳定的无效结果
     */
    ParseResult parse(String content);

    /** 事件文本解析的封闭结果类型。 */
    sealed interface ParseResult permits ParseResult.Parsed, ParseResult.Invalid {
        /**
         * 表示模型文本已经转换为有效事件。
         *
         * @param event 已验证事件
         */
        record Parsed(EventData event) implements ParseResult {
            /** 校验解析成功结果必须包含事件。 */
            public Parsed {
                Objects.requireNonNull(event, "event");
            }
        }

        /**
         * 表示模型文本不符合事件契约。
         *
         * @param reason 无效原因
         */
        record Invalid(String reason) implements ParseResult {
            /** 校验无效结果必须包含非空原因。 */
            public Invalid {
                if (reason == null || reason.isBlank()) {
                    throw new IllegalArgumentException("parse failure reason cannot be null or blank");
                }
            }
        }
    }
}
