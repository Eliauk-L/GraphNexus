package com.graphnexus.infrastructure.mysql.ops.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 操作审计日志实体（JPA Entity），对应 MySQL {@code audit_log} 表。
 *
 * <p>日志类表，不设 is_deleted。记录用户登录和关键业务操作
 * （文档上传/处理、智能问答）的事件。异步非阻塞写入，
 * 失败不影响主流程。为运营统计的系统使用量指标提供数据源。</p>
 *
 * @author Jay
 * @date 2026/06/23
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "audit_log", indexes = {
    @Index(name = "idx_audit_user", columnList = "user_id"),
    @Index(name = "idx_audit_time", columnList = "create_time"),
    @Index(name = "idx_audit_type", columnList = "operation_type")
})
@EntityListeners(AuditingEntityListener.class)
public class AuditLogDO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", length = 32, nullable = false)
    private OperationType operationType;

    @Column(name = "resource_id", length = 128)
    private String resourceId;

    @CreatedDate
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;
}