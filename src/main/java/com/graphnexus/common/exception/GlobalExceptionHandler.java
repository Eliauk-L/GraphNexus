package com.graphnexus.common.exception;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * <p>拦截所有 Controller 层抛出的异常，统一转换为 {@link ErrorResponse} 并返回。
 * 这是 L1 层的最后防线——禁止在此层向上抛异常。</p>
 *
 * <p>处理的异常类型（按 DESIGN.md §2.2 异常处理流）：</p>
 * <ul>
 *   <li>{@link BusinessException} → 按 ErrorCode 映射 HTTP 状态码（4xx/5xx/502）</li>
 *   <li>{@link MethodArgumentNotValidException} → 400 Bad Request</li>
 *   <li>{@link AccessDeniedException} → 403 Forbidden</li>
 *   <li>{@link Exception} → 500 Internal Server Error（兜底，隐藏内部细节）</li>
 * </ul>
 *
 * @author GraphNexus
 * @date 2026/06/11
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常处理。
     *
     * @param ex 业务异常
     * @return ResponseEntity 含 ErrorResponse body 及对应 HTTP 状态码
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        ErrorResponse body = ErrorResponse.of(ex, getTraceId());
        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }

    /**
     * 参数校验失败（@Valid 触发）。
     *
     * @param ex 校验异常
     * @return 400 + 字段错误拼接
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ErrorResponse body = new ErrorResponse(
                ErrorCode.A0002.getErrorCode(),
                "参数校验失败: " + fieldErrors,
                fieldErrors,
                getTraceId(),
                java.time.LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 权限不足处理。
     *
     * @param ex 权限异常
     * @return 403
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        ErrorResponse body = new ErrorResponse(
                ErrorCode.A0003.getErrorCode(),
                "权限不足: " + ex.getMessage(),
                ErrorCode.A0003.getDefaultUserTip(),
                getTraceId(),
                java.time.LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /**
     * 兜底异常处理——所有未明确捕获的异常在此统一处理。
     * 隐藏内部错误细节，只返回 B0001 通用系统错误。
     *
     * @param ex 未处理异常
     * @return 500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        ErrorResponse body = new ErrorResponse(
                ErrorCode.B0001.getErrorCode(),
                "系统内部异常: " + ex.getClass().getSimpleName(),
                ErrorCode.B0001.getDefaultUserTip(),
                getTraceId(),
                java.time.LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /**
     * 从 MDC 获取当前 traceId。
     */
    private String getTraceId() {
        try {
            String traceId = MDC.get("traceId");
            return traceId != null ? traceId : "";
        } catch (Exception e) {
            return "";
        }
    }

    }