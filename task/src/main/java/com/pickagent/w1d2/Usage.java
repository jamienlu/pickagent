package com.pickagent.w1d2;

import lombok.Getter;
import lombok.Setter;

/**
 * W1D2 用于理解 Token 用量的学习型 DTO。
 *
 * <p>字段名沿用早期练习，不代表 Responses API 的正式 usage 字段。</p>
 *
 * @author jamieLu
 * @since 2026-08-24
 */
@Getter
@Setter
public class Usage {
    /** 输入侧 Token 数的早期学习字段。 */
    private int prompt_tokens;
    /** 输出侧 Token 数的早期学习字段。 */
    private int completion_tokens;
    /** 输入与输出 Token 总数。 */
    private int total_tokens;

    /** 创建空的 Token 用量学习对象，字段通过访问器填充。 */
    public Usage() {
    }
}
