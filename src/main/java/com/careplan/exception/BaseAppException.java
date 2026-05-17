package com.careplan.exception;

import org.springframework.http.HttpStatus;

/**
 * 统一异常基类
 * 所有业务异常继承这个，GlobalExceptionHandler 统一处理
 */
public class BaseAppException extends RuntimeException {

    private final String type;       // "validation_error" / "block" / "warning"
    private final String code;       // "invalid_npi" / "duplicate_order" / "possible_duplicate_patient"
    private final String detail;     // 详细说明
    private final HttpStatus httpStatus;

    public BaseAppException(String type, String code, String message, String detail, HttpStatus httpStatus) {
        super(message);
        this.type = type;
        this.code = code;
        this.detail = detail;
        this.httpStatus = httpStatus;
    }

    public String getType() { return type; }
    public String getCode() { return code; }
    public String getDetail() { return detail; }
    public HttpStatus getHttpStatus() { return httpStatus; }
}
