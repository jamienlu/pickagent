package com.pickagent.assistant.web;

import com.pickagent.assistant.application.AssistantModelException;
import com.pickagent.assistant.application.InvalidAssistantInputException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps application failures to explicit HTTP behavior. */
@RestControllerAdvice
public final class AssistantExceptionHandler {
    /** Maps blank user input to HTTP 400. */
    @ExceptionHandler(InvalidAssistantInputException.class)
    public ResponseEntity<ApiError> invalidInput(InvalidAssistantInputException failure) {
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_INPUT", failure.getMessage()));
    }

    /** Maps model/provider failures to HTTP 502. */
    @ExceptionHandler(AssistantModelException.class)
    public ResponseEntity<ApiError> modelFailure(AssistantModelException failure) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("MODEL_FAILURE", failure.getMessage()));
    }
}
