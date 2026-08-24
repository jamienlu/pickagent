package com.pickagent.w1d2;

import lombok.Getter;
import lombok.Setter;

/**
 * @author jamieLu
 * @create 2026-08-24
 */
@Getter
@Setter
public class UnknownItem extends MessageItem {
    private String unknownField;

    public String getUnknownField() {
        return unknownField;
    }

    public void setUnknownField(String unknownField) {
        this.unknownField = unknownField;
    }
}
