package com.careplan.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.careplan.adapter.AdapterParseException;
import com.careplan.adapter.AdapterValidationException;

/**
 * 全局异常处理器
 *
 * 所有异常统一转成这个 JSON 格式：
 * {
 *   "success": false,
 *   "error": {
 *     "type": "validation_error" / "block" / "warning",
 *     "code": "invalid_npi",
 *     "message": "NPI must be 10 digits",
 *     "detail": "...",
 *     "warnings": [...]      // 只有 warning 类型才有
 *   },
 *   "timestamp": "2026-05-16T10:30:00"
 * }
 *
 * 前端只要检查 success == true/false，然后看 error.type 就知道怎么处理。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Logger 可以用来记录异常日志，方便排查问题
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理我们自定义的 BaseAppException（ValidationError, BlockError, WarningException）
     */
    @ExceptionHandler(BaseAppException.class)
    public ResponseEntity<Map<String, Object>> handleAppException(BaseAppException ex) {
        if (ex instanceof WarningException) {
            log.warn("[{}] {}", ex.getCode(), ex.getMessage());
        } else {
            log.error("[{}] {}", ex.getCode(), ex.getMessage());
        }        
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("type", ex.getType());
        error.put("code", ex.getCode());
        error.put("message", ex.getMessage());

        if (ex.getDetail() != null) {
            error.put("detail", ex.getDetail());
        }

        // WarningException 额外带 warnings 列表
        if (ex instanceof WarningException warningEx) {
            error.put("warnings", warningEx.getWarnings());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", error);
        body.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }

    /**
     * 处理 Spring Bean Validation 的异常（@NotNull, @Size 等 DTO 上的注解校验失败）
     * 对应你说的"兼容 DRF 自带的 ValidationError"
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        log.error("[invalid_input] Input validation failed: {}", ex.getBindingResult().getFieldErrors());
        List<String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("type", "validation_error");
        error.put("code", "invalid_input");
        error.put("message", "Input validation failed");
        error.put("detail", fieldErrors);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", error);
        body.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 兜底：处理所有未预期的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("[unexpected] Internal server error: {}", ex.getMessage(), ex);
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("type", "internal_error");
        error.put("code", "unexpected");
        error.put("message", "Internal server error");
        error.put("detail", ex.getMessage());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", error);
        body.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(AdapterParseException.class)
    public ResponseEntity<Map<String, Object>> handleAdapterParse(AdapterParseException ex) {
        log.error("[adapter_parse] {}", ex.getMessage());
        Map<String, Object> body = Map.of(
                "success", false,
                "error", Map.of(
                        "type", "validation",
                        "code", "adapter_parse_error",
                        "message", ex.getMessage()
                ),
                "timestamp", LocalDateTime.now()
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(AdapterValidationException.class)
    public ResponseEntity<Map<String, Object>> handleAdapterValidation(AdapterValidationException ex) {
        log.warn("[adapter_validation] {}", ex.getMessage());
        Map<String, Object> body = Map.of(
                "success", false,
                "error", Map.of(
                        "type", "validation",
                        "code", "adapter_validation_error",
                        "message", ex.getMessage(),
                        "details", ex.getErrors()
                ),
                "timestamp", LocalDateTime.now()
        );
        return ResponseEntity.badRequest().body(body);
    }


}
