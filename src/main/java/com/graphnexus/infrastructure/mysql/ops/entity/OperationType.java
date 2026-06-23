package com.graphnexus.infrastructure.mysql.ops.entity;

/**
 * 操作类型枚举 — 审计日志记录的操作分类。
 *
 * @author Jay
 * @date 2026/06/23
 */
public enum OperationType {

    /** 用户登录 */
    LOGIN,

    /** 文档上传（PDF/TXT） */
    DOCUMENT_UPLOAD,

    /** 文档处理（解析触发） */
    DOCUMENT_PROCESS,

    /** 智能问答 */
    QA_ASK
}