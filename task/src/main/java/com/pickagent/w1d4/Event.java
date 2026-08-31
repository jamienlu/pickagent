package com.pickagent.w1d4;

import java.util.List;
import java.util.Objects;

/**
 * 从结构化模型输出中提取的事件值对象。
 *
 * @param name 事件名称
 * @param date 事件日期文本
 * @param participants 参与者列表，构造时进行防御性复制
 * @author jamieLu
 * @since 2026-08-27
 */
public record Event(String name, String date, List<String> participants) {
    /** 校验必填字段并防御性复制参与者列表。 */
    public Event {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(date, "date");
        participants = List.copyOf(Objects.requireNonNull(participants, "participants"));
    }
}
