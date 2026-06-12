package com.graphnexus.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 统一错误码枚举。
 *
 * <p>编码规则：5 位字符串 = 来源（A/B/C）+ 4 位数字。
 * A = 用户端错误，B = 系统错误，C = 第三方错误。</p>
 *
 * @author Jay
 * @date 2026/06/11
 */
public enum ErrorCode {

    // ======================== A · 用户端错误 ========================

    /** 请求的资源不存在 */
    A0001("A0001", HttpStatus.NOT_FOUND, "请求的资源不存在，请检查参数"),

    /** 参数校验失败 */
    A0002("A0002", HttpStatus.BAD_REQUEST, "请求参数不符合要求，请检查输入"),

    /** 无权限访问 */
    A0003("A0003", HttpStatus.FORBIDDEN, "您没有权限执行此操作"),

    /** 文件类型不支持 */
    A0004("A0004", HttpStatus.BAD_REQUEST, "仅支持 PDF 格式文件"),

    /** 文件大小超限 */
    A0005("A0005", HttpStatus.PAYLOAD_TOO_LARGE, "文件大小不能超过 50MB"),

    /** 文档不存在 */
    A0006("A0006", HttpStatus.NOT_FOUND, "文档记录不存在或已被删除"),

    /** 文档内容重复 */
    A0007("A0007", HttpStatus.CONFLICT, "该学科下已存在相同内容的文档"),

    // ======================== B · 系统错误 ========================

    /** 系统内部错误（兜底） */
    B0001("B0001", HttpStatus.INTERNAL_SERVER_ERROR, "系统内部异常，请联系管理员"),

    /** 服务暂不可用 */
    B0002("B0002", HttpStatus.SERVICE_UNAVAILABLE, "服务暂时不可用，请稍后重试"),

    // ======================== C · 第三方错误 ========================

    /** 外部服务调用失败 */
    C0001("C0001", HttpStatus.BAD_GATEWAY, "外部服务调用失败，请稍后重试");

    private final String errorCode;
    private final HttpStatus httpStatus;
    private final String defaultUserTip;

    ErrorCode(String errorCode, HttpStatus httpStatus, String defaultUserTip) {
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.defaultUserTip = defaultUserTip;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public int getHttpStatusCode() {
        return httpStatus.value();
    }

    public String getDefaultUserTip() {
        return defaultUserTip;
    }
}