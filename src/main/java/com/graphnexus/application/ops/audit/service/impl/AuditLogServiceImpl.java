package com.graphnexus.application.ops.audit.service.impl;

import com.graphnexus.application.ops.audit.service.AuditLogService;
import com.graphnexus.infrastructure.mysql.ops.entity.AuditLogDO;
import com.graphnexus.infrastructure.mysql.ops.entity.OperationType;
import com.graphnexus.infrastructure.mysql.ops.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 操作审计日志异步写入实现。
 *
 * <p>复用既有的 {@code queryAsyncExecutor} 线程池，
 * 独立短事务写入，失败仅记 WARN 日志不抛异常。
 * 见 ADR-051。</p>
 *
 * @author Jay
 * @date 2026/06/23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Override
    @Async("queryAsyncExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long userId, OperationType type, String resourceId) {
        try {
            AuditLogDO log = AuditLogDO.builder()
                    .userId(userId)
                    .operationType(type)
                    .resourceId(resourceId)
                    .build();
            auditLogRepository.save(log);
        } catch (Exception e) {
            log.warn("审计日志写入失败: userId={}, type={}, resourceId={}, reason={}",
                    userId, type, resourceId, e.getMessage());
        }
    }

    @Override
    public void record(Long userId, OperationType type) {
        record(userId, type, null);
    }
}