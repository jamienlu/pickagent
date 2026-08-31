package com.pickagent.w1d2;

import lombok.Getter;
import lombok.Setter;

/**
 * W1D2 学习模型中的文本值对象。
 *
 * @author jamieLu
 * @since 2026-08-24
 */
@Getter
@Setter
public class OutputText {
    /** 模型输出文本。 */
    private String text;

    /** 创建空的学习型文本对象，文本通过访问器填充。 */
    public OutputText() {
    }
}
