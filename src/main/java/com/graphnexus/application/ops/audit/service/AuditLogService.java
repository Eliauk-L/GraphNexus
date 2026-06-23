package com.graphnexus.application.ops.audit.service;

import com.graphnexus.infrastructure.mysql.ops.entity.OperationType;

/**
 * 操作审计日志写入服务接口。
 * 异步非阻塞写入，失败仅记 WARN 日志不影响主流程（AC-8）。
 *
 * @author Jay
 * @date 2026/06/23
 */
public interface AuditLogService {

    /**
     * 记录一条操作审计日志（异步 fire-and-forget）。
     *
     * @param userId      操作人 ID
     * @param type        操作类型
     * @param resourceId  关联资源 ID（可选，如 documentId / taskId）
     */
    void record(Long userId, OperationType type, String resourceId);

    /**
     * 记录一条操作审计日志（无关联资源）。
     */
    void record(Long userId, OperationType type);
}