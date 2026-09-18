package io.github.jamielu.assistant.web;

/**
 * 稳定的 HTTP 错误表示。
 *
 * @param code 稳定的机器可读错误码
 * @param message 面向调用方的错误消息
 */
public record ApiError(String code, String message) {
}
