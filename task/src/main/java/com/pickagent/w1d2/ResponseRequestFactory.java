package com.pickagent.w1d2;

import com.openai.models.responses.ResponseCreateParams;
import lombok.Builder;

/**
 * 根据学习参数构建 OpenAI SDK 的 Responses 请求。
 *
 * <p>该工厂集中执行最小输入校验，避免 Demo 直接拼装 SDK 参数。</p>
 *
 * @author jamieLu
 * @since 2026-08-25
 */
public class ResponseRequestFactory {
    /** 模型标识。 */
    private String model;
    /** 用户输入。 */
    private String input;
    /** 当前请求的高优先级指令。 */
    private String instructions;
    /** 允许模型生成的最大输出 Token 数。 */
    private long maxOutputTokens;
    /** 是否由供应商保存响应。 */
    private boolean store;

   /**
    * 创建请求参数工厂；通常通过 Lombok 生成的 {@code builder()} 调用。
    *
    * @param model 模型标识
    * @param input 用户输入
    * @param instructions 当前请求指令
    * @param maxOutputTokens 最大输出 Token 数
    * @param store 是否保存响应
    */
   @Builder
   public ResponseRequestFactory(
           String model,
           String input,
           String instructions,
           long maxOutputTokens,
           boolean store
   ) {
       this.model = model;
       this.input = input;
       this.instructions = instructions;
       this.maxOutputTokens = maxOutputTokens;
       this.store = store;
   }

   /**
    * 构建经过最小校验的 SDK 请求参数。
    *
    * @return OpenAI SDK Responses 请求参数
    * @throws IllegalArgumentException 输入为空或最大输出 Token 数非正时抛出
    */
   public ResponseCreateParams builderResponseParams() {
       if (input == null || input.isEmpty()) {
           throw new IllegalArgumentException("prompt cannot be null or empty");
       }
       if (maxOutputTokens <= 0) {
           throw new IllegalArgumentException("maxOutputTokens cannot be zero or negative");
       }
       return ResponseCreateParams.builder().model(model).
               input(input).
               instructions(instructions).
               maxOutputTokens(maxOutputTokens).
               store(store).
               build();
   }
}
