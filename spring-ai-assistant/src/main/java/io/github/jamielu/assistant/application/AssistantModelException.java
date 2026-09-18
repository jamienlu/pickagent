package io.github.jamielu.assistant.application;

/** 在应用边界封装模型调用失败。 */
public final class AssistantModelException extends RuntimeException {
    /**
     * 创建模型失败异常，并保留供应商异常作为原因。
     *
     * @param cause 底层模型失败
     */
    public AssistantModelException(Throwable cause) {
        super("assistant model invocation failed", cause);
    }

    /**
     * 为无效模型响应创建失败异常。
     *
     * @param message 稳定的错误消息
     */
    public AssistantModelException(String message) {
        super(message);
    }
}
