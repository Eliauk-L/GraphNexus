package com.graphnexus.common.exception;

/**
 * 统一业务异常。
 *
 * <p>所有业务层（L2 Service）主动抛出的异常均使用此类或其子类。
 * 由 L1 的 {@link GlobalExceptionHandler} 统一拦截并转换为 {@link ErrorResponse}。</p>
 *
 * <p>使用示例：
 * <pre>{@code
 * throw new BusinessException(ErrorCode.A0001);
 * throw new BusinessException(ErrorCode.A0002, "学生姓名不能为空");
 * throw new BusinessException(ErrorCode.B0001, "图谱构建失败", "请检查 PDF 文件是否完整");
 * }</pre></p>
 *
 * @author Jay
 * @date 2026/06/11
 */
public class BusinessException extends RuntimeException {

    /** 错误码 */
    private final String errorCode;

    /** 对应的 HTTP 状态码 */
    private final int httpStatus;

    /** 开发者可读的简要描述（面向日志/排查） */
    private final String errorMessage;

    /** 用户提示信息（可展示给前端） */
    private final String userTip;

    /**
     * 仅错误码，errorMessage 和 userTip 使用枚举默认值。
     *
     * @param errorCode 错误码枚举
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultUserTip());
        this.errorCode = errorCode.getErrorCode();
        this.httpStatus = errorCode.getHttpStatusCode();
        this.errorMessage = errorCode.getDefaultUserTip();
        this.userTip = errorCode.getDefaultUserTip();
    }

    /**
     * 错误码 + 开发者描述，userTip 使用枚举默认值。
     *
     * @param errorCode    错误码枚举
     * @param errorMessage 面向开发者的简要描述
     */
    public BusinessException(ErrorCode errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode.getErrorCode();
        this.httpStatus = errorCode.getHttpStatusCode();
        this.errorMessage = errorMessage;
        this.userTip = errorCode.getDefaultUserTip();
    }

    /**
     * 错误码 + 开发者描述 + 用户提示。
     *
     * @param errorCode    错误码枚举
     * @param errorMessage 面向开发者的简要描述
     * @param userTip      可展示给用户的提示信息
     */
    public BusinessException(ErrorCode errorCode, String errorMessage, String userTip) {
        super(errorMessage);
        this.errorCode = errorCode.getErrorCode();
        this.httpStatus = errorCode.getHttpStatusCode();
        this.errorMessage = errorMessage;
        this.userTip = userTip;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getUserTip() {
        return userTip;
    }
}