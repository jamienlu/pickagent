package com.pickagent.w1d2;

import lombok.Getter;
import lombok.Setter;

/**
 * W1D2 用于理解响应内容层级的学习型 DTO。
 *
 * <p>该类型不是 OpenAI SDK 的传输对象，不应用于真实 API 反序列化。</p>
 *
 * @author jamieLu
 * @since 2026-08-24
 */
@Getter
@Setter
public class ContentItem {
    /** 内容项类型。 */
    private String type;
    /** 学习模型中的文本内容。 */
    private OutputText text;

    /** 创建空的学习型内容项，字段通过访问器填充。 */
    public ContentItem() {
    }
}
