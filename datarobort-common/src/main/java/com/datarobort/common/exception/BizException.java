package com.datarobort.common.exception;

import com.datarobort.common.error.ErrorCode;
import lombok.Getter;

/**
 * Business exception, handled by the global exception handler and
 * converted to a unified {@code Result}.
 */
@Getter
public class BizException extends RuntimeException {

    private final String code;

    public BizException(ErrorCode ec) {
        super(ec.getMessage());
        this.code = ec.getCode();
    }

    public BizException(ErrorCode ec, String detail) {
        super(ec.getMessage() + ": " + detail);
        this.code = ec.getCode();
    }

    public BizException(String code, String message) {
        super(message);
        this.code = code;
    }
}
