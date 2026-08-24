package com.pickagent.w1d2;

import lombok.Getter;
import lombok.Setter;

/**
 * @author jamieLu
 * @create 2026-08-24
 */
@Getter
@Setter
public class ResponseEnvelope {
   private OutputItem[] outputItems;
   private Usage usage;
}
