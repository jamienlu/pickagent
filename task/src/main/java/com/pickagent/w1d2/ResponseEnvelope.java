package com.pickagent.w1d2;

import lombok.Getter;
import lombok.Setter;

/**
 * W1D2 用于理解响应包络层级的学习型 DTO。
 *
 * <p>该类型不作为真实供应商协议或生产集成契约。</p>
 *
 * @author jamieLu
 * @since 2026-08-24
 */
@Getter
@Setter
public class ResponseEnvelope {
   /** 异构输出项的学习模型。 */
   private OutputItem[] outputItems;
   /** Token 用量的学习模型。 */
   private Usage usage;

   /** 创建空的学习型响应包络，字段通过访问器填充。 */
   public ResponseEnvelope() {
   }
}
