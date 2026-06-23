package com.graphnexus.infrastructure.mysql.ops.entity;

/**
 * 操作类型枚举 — 审计日志记录的操作分类。
 *
 * @author Jay
 * @date 2026/06/23
 */
public enum OperationType {

    LOGIN,
    DOCUMENT_UPLOAD,
    DOCUMENT_PROCESS,
    QA_ASK
}