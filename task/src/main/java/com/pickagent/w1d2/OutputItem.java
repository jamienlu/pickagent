package com.pickagent.w1d2;

import lombok.Getter;
import lombok.Setter;

/**
 * W1D2 用于理解响应顶层输出项的学习型 DTO。
 *
 * <p>真实 Responses API 的 output 是异构集合，应使用 SDK 类型化分支处理。</p>
 *
 * @author jamieLu
 * @since 2026-08-24
 */
@Getter
@Setter
public class OutputItem {
    /** 输出项类型。 */
    private String type;
    /** 学习模型中的消息条目。 */
    private MessageItem msgItem;

    /** 创建空的学习型输出项，字段通过访问器填充。 */
    public OutputItem() {
    }
}
