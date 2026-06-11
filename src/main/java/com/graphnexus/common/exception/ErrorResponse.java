package com.graphnexus.common.exception;

import java.time.LocalDateTime;

/**
 * 统一错误响应体（不可变 record）。
 *
 * <p>由 {@link GlobalExceptionHandler} 在异常路径中构造，
 * 通过 HTTP Response Body 返回给客户端。</p>
 *
 * @param errorCode    5 位错误码（如 "A0001"）
 * @param errorMessage 开发者可读的简要描述
 * @param userTip      用户提示信息
 * @param traceId      全链路追踪 ID
 * @param timestamp    错误发生时间
 * @author Jay
 * @date 2026/06/11
 */
public record ErrorResponse(
        String errorCode,
        String errorMessage,
        String userTip,
        String traceId,
        LocalDateTime timestamp
) {

    /**
     * 从 BusinessException 构造。
     *
     * @param ex      业务异常
     * @param traceId 全链路追踪 ID
     * @return ErrorResponse
     */
    public static ErrorResponse of(BusinessException ex, String traceId) {
        return new ErrorResponse(
                ex.getErrorCode(),
                ex.getErrorMessage(),
                ex.getUserTip(),
                traceId,
                LocalDateTime.now()
        );
    }

    /**
     * 兜底构造（用于非 BusinessException 的异常）。
     *
     * @param errorCode    错误码
     * @param errorMessage 错误描述
     * @param traceId      全链路追踪 ID
     * @return ErrorResponse
     */
    public static ErrorResponse of(String errorCode, String errorMessage, String traceId) {
        return new ErrorResponse(
                errorCode,
                errorMessage,
                errorMessage,
                traceId,
                LocalDateTime.now()
        );
    }
}