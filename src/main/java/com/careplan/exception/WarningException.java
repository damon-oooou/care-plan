package com.careplan.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * 业务警告（可能是重复患者、可能是续方等）
 * HTTP 200 但带 warnings 字段
 * 用户传 confirmWarnings=true 可跳过
 */
public class WarningException extends BaseAppException {

    private final List<String> warnings;

    public WarningException(String code, String message, List<String> warnings) {
        super("warning", code, message, null, HttpStatus.OK);
        this.warnings = warnings;
    }

    public List<String> getWarnings() { return warnings; }
}
