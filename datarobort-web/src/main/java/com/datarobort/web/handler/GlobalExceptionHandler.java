package com.datarobort.web.handler;

import com.datarobort.common.error.ErrorCode;
import com.datarobort.common.exception.BizException;
import com.datarobort.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;

import java.util.stream.Collectors;

/**
 * Global exception handler: converts exceptions into the unified Result body.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.warn("business error, code={}, msg={}", e.getCode(), e.getMessage());
        return fill(Result.error(e.getCode(), e.getMessage()));
    }

    /** Bean Validation failures on @RequestBody / @ModelAttribute. */
    @ExceptionHandler(WebExchangeBindException.class)
    public Result<Void> handleValidation(WebExchangeBindException e) {
        String detail = e.getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("validation failed: {}", detail);
        return fill(Result.error(ErrorCode.PARAM_INVALID, detail));
    }

    /** Missing request params, type mismatch, unreadable body, etc. */
    @ExceptionHandler(ServerWebInputException.class)
    public Result<Void> handleInput(ServerWebInputException e) {
        log.warn("bad input: {}", e.getReason());
        return fill(Result.error(ErrorCode.PARAM_MISSING, String.valueOf(e.getReason())));
    }

    /** Preserve HTTP status (404 etc.) instead of flattening to 200 + SYSTEM_ERROR. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<Void>> handleStatus(ResponseStatusException e) {
        String code = e.getStatusCode().value() == 404
                ? ErrorCode.NOT_FOUND.getCode()
                : ErrorCode.SYSTEM_ERROR.getCode();
        String message = e.getStatusCode().value() == 404
                ? ErrorCode.NOT_FOUND.getMessage()
                : e.getReason();
        Result<Void> body = fill(Result.error(code, message));
        return ResponseEntity.status(e.getStatusCode()).body(body);
    }

    @ExceptionHandler(Throwable.class)
    public Result<Void> handleUnknown(Throwable e) {
        log.error("unexpected system error", e);
        return fill(Result.error(ErrorCode.SYSTEM_ERROR));
    }

    private <T> Result<T> fill(Result<T> result) {
        result.setTraceId(MDC.get("traceId"));
        return result;
    }
}
