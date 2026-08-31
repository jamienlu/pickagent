package com.pickagent.w1d2;

import lombok.Getter;
import lombok.Setter;

/**
 * 表示提取器尚不认识的输出项，用于验证异构输出遍历不能依赖固定索引。
 *
 * @author jamieLu
 * @since 2026-08-24
 */
@Getter
@Setter
public class UnknownItem extends MessageItem {
    /** 未知字段的示例值。 */
    private String unknownField;

    /** 创建空的未知输出项。 */
    public UnknownItem() {
    }

    /**
     * 获取未知字段值。
     *
     * @return 未知字段值
     */
    public String getUnknownField() {
        return unknownField;
    }

    /**
     * 设置未知字段值。
     *
     * @param unknownField 未知字段值
     */
    public void setUnknownField(String unknownField) {
        this.unknownField = unknownField;
    }
}
