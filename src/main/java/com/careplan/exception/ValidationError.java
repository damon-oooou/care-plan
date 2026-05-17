package com.careplan.exception;

import org.springframework.http.HttpStatus;

/**
 * 输入格式错误（NPI 不是 10 位、MRN 不是 6 位等）
 * HTTP 400
 */
public class ValidationError extends BaseAppException {

    public ValidationError(String code, String message, String detail) {
        super("validation_error", code, message, detail, HttpStatus.BAD_REQUEST);
    }

    public ValidationError(String code, String message) {
        this(code, message, null);
    }
}
