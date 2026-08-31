package com.pickagent.w1d2;

import lombok.Getter;
import lombok.Setter;

/**
 * W1D2 用于理解消息输出层级的学习型 DTO。
 *
 * <p>字段结构仅服务于练习，不代表 OpenAI Responses API 的正式协议模型。</p>
 *
 * @author jamieLu
 * @since 2026-08-24
 */
@Getter
@Setter
public class MessageItem {
    /** 消息角色。 */
    private String role;
    /** 学习模型中的内容项集合。 */
    private ContentItem[] output;

    /** 创建空的学习型消息项，字段通过访问器填充。 */
    public MessageItem() {
    }
}
