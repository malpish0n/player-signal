package com.playersignal.shared;

import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrors {
    public record ErrorBody(String code, String message, String requestId) {}

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> domain(ApiException error) {
        return response(error.status(), error.code(), error.getMessage());
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorBody> invalid(Exception error) {
        return response(400, "INVALID_REQUEST", "Check the request body, identifier and filter values.");
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> unexpected(Exception error) {
        String requestId = java.util.Objects.requireNonNullElseGet(org.slf4j.MDC.get("requestId"),()->UUID.randomUUID().toString());
        LoggerFactory.getLogger(ApiErrors.class).error("Request failed requestId={}", requestId, error);
        return ResponseEntity.internalServerError().body(new ErrorBody(
                "INTERNAL_ERROR", "The request failed. Retry or inspect the server logs using the request ID.", requestId));
    }
    private ResponseEntity<ErrorBody> response(int status, String code, String message) {
        String requestId = java.util.Objects.requireNonNullElseGet(org.slf4j.MDC.get("requestId"),()->UUID.randomUUID().toString());
        LoggerFactory.getLogger(ApiErrors.class).warn("Request rejected requestId={} code={}", requestId, code);
        return ResponseEntity.status(status).body(new ErrorBody(code, message, requestId));
    }
}
