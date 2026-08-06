package com.datarobort.common.result;

import com.datarobort.common.error.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * Unified API response body.
 *
 * @param <T> payload type
 */
@Data
public class Result<T> implements Serializable {

    /** Business code, "0" means success. */
    private String code;

    /** Human-readable message. */
    private String message;

    /** Response payload. */
    private T data;

    /** Distributed trace id, copied from MDC when present. */
    private String traceId;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.setCode(ErrorCode.SUCCESS.getCode());
        r.setData(data);
        return r;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> error(ErrorCode ec) {
        return error(ec.getCode(), ec.getMessage());
    }

    public static <T> Result<T> error(ErrorCode ec, String detail) {
        return error(ec.getCode(), ec.getMessage() + ": " + detail);
    }

    public static <T> Result<T> error(String code, String message) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isSuccess() {
        return ErrorCode.SUCCESS.getCode().equals(this.code);
    }
}
