package com.careplan.adapter;

/**
 * Adapter 解析原始数据失败时抛出。
 * 例如：JSON 格式错误、缺少必填字段。
 * GlobalExceptionHandler 捕获后返回 400。
 */
public class AdapterParseException extends RuntimeException {

    private final String sourceSystem;

    public AdapterParseException(String sourceSystem, String message) {
        super("[" + sourceSystem + "] Parse failed: " + message);
        this.sourceSystem = sourceSystem;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }
}
