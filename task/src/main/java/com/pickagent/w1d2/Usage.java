package com.pickagent.w1d2;

import lombok.Getter;
import lombok.Setter;

/**
 * @author jamieLu
 * @create 2026-08-24
 */
@Getter
@Setter
public class Usage {
    private int prompt_tokens;
    private int completion_tokens;
    private int total_tokens;
}
