package io.github.jamielu.assistant.application;

/** 表示调用方没有提供可用的用户内容。 */
public final class InvalidAssistantInputException extends RuntimeException {
    /** 使用稳定的诊断消息创建无效输入异常。 */
    public InvalidAssistantInputException() {
        super("message must not be blank");
    }
}
