package com.careplan.adapter;

import java.util.List;

/**
 * Adapter 校验 InternalOrder 失败时抛出。
 * 例如：MRN 格式不对、必填字段为空。
 * GlobalExceptionHandler 捕获后返回 400，附带具体错误列表。
 */
public class AdapterValidationException extends RuntimeException {

    private final String sourceSystem;
    private final List<String> errors;

    public AdapterValidationException(String sourceSystem, List<String> errors) {
        super("[" + sourceSystem + "] Validation failed: " + String.join("; ", errors));
        this.sourceSystem = sourceSystem;
        this.errors = errors;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public List<String> getErrors() {
        return errors;
    }
}
