package com.pickagent.w1d4;

import java.util.Objects;

/**
 * 结构化文本解码的封闭结果类型，显式区分成功事件和失败原因。
 *
 * @author jamieLu
 * @since 2026-08-27
 */
public sealed interface DecodeResult permits DecodeResult.Success, DecodeResult.Failure {

    /**
     * 表示文本已经通过契约校验并转换为事件。
     *
     * @param event 解码后的事件
     */
    record Success(Event event) implements DecodeResult {
        /** 校验成功结果必须包含事件。 */
        public Success {
            Objects.requireNonNull(event, "event");
        }
    }

    /**
     * 表示文本未通过结构或业务边界校验。
     *
     * @param code 稳定的错误分类
     * @param reason 便于诊断的失败原因
     */
    record Failure(ErrorCode code, String reason) implements DecodeResult {
        /** 校验失败结果必须包含错误分类和原因。 */
        public Failure {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** 解码失败的稳定分类。 */
    enum ErrorCode {
        /** 输入文本为空。 */
        EMPTY_TEXT,
        /** 输入不是合法 JSON。 */
        MALFORMED_JSON,
        /** JSON 顶层不是对象。 */
        NOT_JSON_OBJECT,
        /** 缺少必需字段。 */
        MISSING_FIELDS,
        /** 出现契约外字段。 */
        EXTRA_FIELDS,
        /** 字段类型与契约不符。 */
        WRONG_FIELD_TYPE,
        /** 校验后的值无法构造领域对象。 */
        CONVERSION_FAILED
    }
}
