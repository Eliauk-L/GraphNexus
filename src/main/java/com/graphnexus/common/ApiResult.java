package com.graphnexus.common;

import com.graphnexus.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 统一 API 响应体（不可变 record）。
 *
 * <p>所有 Controller 返回数据时必须使用此类包装，禁止直接返回裸数据。
 * 正常路径用 {@link #success(Object)}，异常路径用 {@link #error(ErrorResponse, int)}。</p>
 *
 * @param code      HTTP 状态码
 * @param message   提示信息
 * @param data      业务数据（可为 null）
 * @param traceId   全链路追踪 ID
 * @param timestamp 响应时间戳（毫秒）
 * @param <T>       业务数据类型
 * @author Jay
 * @date 2026/06/11
 */
public record ApiResult<T>(
        @Schema(description = "HTTP 状态码", example = "200") int code,
        @Schema(description = "提示信息", example = "success") String message,
        @Schema(description = "业务数据（可为 null）") T data,
        @Schema(description = "全链路追踪 ID", example = "a1b2c3d4e5f6") String traceId,
        @Schema(description = "响应时间戳（毫秒）", example = "1718000000000") long timestamp
) {

    /** 成功 HTTP 状态码 */
    private static final int SUCCESS_CODE = 200;
    /** 成功默认消息 */
    private static final String SUCCESS_MESSAGE = "success";

    /**
     * 成功响应。
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return ApiResult（code=200, message="success"）
     */
    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(
                SUCCESS_CODE,
                SUCCESS_MESSAGE,
                data,
                getCurrentTraceId(),
                System.currentTimeMillis()
        );
    }

    /**
     * 错误响应。
     *
     * @param error      错误详情（来自 GlobalExceptionHandler）
     * @param httpStatus HTTP 状态码
     * @param <T>        数据类型（通常为 null）
     * @return ApiResult（data=null）
     */
    public static <T> ApiResult<T> error(ErrorResponse error, int httpStatus) {
        return new ApiResult<>(
                httpStatus,
                error.errorMessage(),
                null,
                error.traceId(),
                System.currentTimeMillis()
        );
    }

    /**
     * 从 MDC 获取当前 traceId，取不到则返回空字符串。
     */
    private static String getCurrentTraceId() {
        try {
            String traceId = org.slf4j.MDC.get("traceId");
            return traceId != null ? traceId : "";
        } catch (Exception e) {
            return "";
        }
    }
}