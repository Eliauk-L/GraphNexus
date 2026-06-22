package com.graphnexus.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;

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
 * @author Jay
 * @date 2026/06/11
 */
@Slf4j
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
        log.warn("业务异常 errorCode={} httpStatus={} errorMessage={}",
                ex.getErrorCode(), ex.getHttpStatus(), ex.getErrorMessage());
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
        log.warn("参数校验失败 fieldErrors={}", fieldErrors);
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
        log.warn("权限不足 errorCode={} message={}",
                ErrorCode.A0030.getErrorCode(), ex.getMessage());
        ErrorResponse body = new ErrorResponse(
                ErrorCode.A0030.getErrorCode(),
                "权限不足: " + ex.getMessage(),
                ErrorCode.A0030.getDefaultUserTip(),
                getTraceId(),
                java.time.LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /**
     * 认证失败处理（BadCredentials / AuthenticationException）。
     *
     * @param ex 认证异常
     * @return 401
     */
    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ResponseEntity<ErrorResponse> handleAuthenticationException(Exception ex) {
        log.warn("认证失败 errorCode={} message={}",
                ErrorCode.A0023.getErrorCode(), ex.getMessage());
        ErrorResponse body = ErrorResponse.fromErrorCode(ErrorCode.A0023, getTraceId());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    /**
     * 账号被禁用处理。
     *
     * @param ex 禁用异常
     * @return 403
     */
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabledException(DisabledException ex) {
        log.warn("账号已禁用 errorCode={} message={}",
                ErrorCode.A0024.getErrorCode(), ex.getMessage());
        ErrorResponse body = ErrorResponse.fromErrorCode(ErrorCode.A0024, getTraceId());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /**
     * JWT 签名/格式/过期异常处理。
     */
    @ExceptionHandler({SignatureException.class, MalformedJwtException.class, ExpiredJwtException.class})
    public ResponseEntity<ErrorResponse> handleJwtException(Exception ex) {
        log.debug("JWT 校验失败 type={} message={}", ex.getClass().getSimpleName(), ex.getMessage());
        ErrorResponse body = ErrorResponse.fromErrorCode(ErrorCode.A0026, getTraceId());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
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
        // 兜底=未被预期/未被上游记录的系统故障，完整堆栈（类名+getMessage+cause 链+行号）是定位根因的唯一现场。
        // ex 作为 SLF4J 最后一个参数（不对应 {}）→ logback 自动渲染完整堆栈（DESIGN D2）。
        // traceId 由 logback [%X{traceId}] pattern 注入每行，不写入 message（单一来源，DESIGN D5）。
        log.error("兜底未捕获异常 errorCode={} httpStatus={} exception={}",
                ErrorCode.B0001.getErrorCode(),
                500,
                ex.getClass().getSimpleName(),
                ex);
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