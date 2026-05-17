package com.careplan.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务规则阻止（NPI 冲突、同天重复订单等）
 * HTTP 409
 */
public class BlockError extends BaseAppException {

    public BlockError(String code, String message, String detail) {
        super("block", code, message, detail, HttpStatus.CONFLICT);
    }

    public BlockError(String code, String message) {
        this(code, message, null);
    }
}
