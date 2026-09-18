package io.github.jamielu.assistant.web;

import io.github.jamielu.assistant.application.AssistantModelException;
import io.github.jamielu.assistant.application.InvalidAssistantInputException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将应用失败映射为明确的 HTTP 行为。 */
@RestControllerAdvice
public final class AssistantExceptionHandler {
    /** 创建助手 HTTP 异常处理器。 */
    public AssistantExceptionHandler() {
    }

    /**
     * 将空白用户输入映射为 HTTP 400。
     *
     * @param failure 无效输入异常
     * @return 包含稳定错误结构的响应
     */
    @ExceptionHandler(InvalidAssistantInputException.class)
    public ResponseEntity<ApiError> invalidInput(InvalidAssistantInputException failure) {
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_INPUT", failure.getMessage()));
    }

    /**
     * 将模型或供应商失败映射为 HTTP 502。
     *
     * @param failure 模型调用异常
     * @return 包含稳定错误结构的响应
     */
    @ExceptionHandler(AssistantModelException.class)
    public ResponseEntity<ApiError> modelFailure(AssistantModelException failure) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("MODEL_FAILURE", failure.getMessage()));
    }
}
