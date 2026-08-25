package com.pickagent.w1d2;

import com.openai.models.responses.ResponseCreateParams;
import lombok.Builder;

/**
 * @author jamieLu
 * @create 2026-08-25
 */

@Builder
public class ResponseRequestFactory {
    private String model;
    private String input;
    private String instructions;
    private long maxOutputTokens;
    private boolean store;

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
