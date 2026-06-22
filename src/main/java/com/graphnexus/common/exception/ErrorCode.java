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

    /** 文档文本为空，无法进行图谱抽取 */
    A0008("A0008", HttpStatus.BAD_REQUEST, "文档文本内容为空，无法进行图谱抽取"),

    /** 文档状态不允许抽取 */
    A0009("A0009", HttpStatus.BAD_REQUEST, "文档状态不允许抽取，请先完成文档解析"),

    /** LLM 抽取结果校验失败 */
    A0010("A0010", HttpStatus.BAD_REQUEST, "LLM 返回结果格式不符合预期，请稍后重试"),

    /** CSV 格式错误（表头行数/成绩格式/列数不一致） */
    A0011("A0011", HttpStatus.BAD_REQUEST, "CSV 文件格式不符合要求，请检查表头和成绩格式"),

    /** CSV 缺少必要列（学号/考试编号） */
    A0012("A0012", HttpStatus.BAD_REQUEST, "CSV 文件缺少必要列（学号或考试编号），请检查文件"),

    /** CSV 编码异常（非 UTF-8 且非 GBK） */
    A0013("A0013", HttpStatus.BAD_REQUEST, "CSV 文件编码不支持，请使用 UTF-8 或 GBK 编码"),

    /** 考试编号不存在 */
    A0014("A0014", HttpStatus.CONFLICT, "考试编号不存在，无法执行操作"),

    /** 待删除的考试编号不存在（保留占位） */
    A0015("A0015", HttpStatus.NOT_FOUND, "待删除的考试记录不存在或已被删除"),

    /** 融合日志不存在 */
    A0016("A0016", HttpStatus.NOT_FOUND, "融合日志记录不存在，请检查融合 ID"),

    /** 融合进行中，拒绝并发 */
    A0017("A0017", HttpStatus.CONFLICT, "融合操作正在进行中，请稍后重试"),

    /** 图状态已变更，无法回滚 */
    A0018("A0018", HttpStatus.CONFLICT, "图状态已发生变更，无法回滚到指定融合点"),

    /** 无法识别查询意图 */
    A0019("A0019", HttpStatus.BAD_REQUEST, "无法识别查询意图，请更明确地描述问题"),

    /** 存在多个同名或相似学生 */
    A0020("A0020", HttpStatus.CONFLICT, "存在多个同名或相似学生，请使用学号精确指定"),

    /** 问答任务不存在 */
    A0021("A0021", HttpStatus.NOT_FOUND, "问答任务不存在或已过期"),

    /** 考试编号已存在，拒绝重复上传 */
    A0022("A0022", HttpStatus.CONFLICT, "考试编号已存在，请先删除该考试再重新上传"),

    /** 导出记录数超过上限 */
    A0023("A0023", HttpStatus.BAD_REQUEST, "导出记录数超过上限（5000 条），请缩小筛选范围"),

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