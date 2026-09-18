package io.github.jamielu.assistant.web;

/**
 * 传入的助手请求。
 *
 * @param message 用户消息
 */
public record ChatRequest(String message) {
}
