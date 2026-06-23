package com.graphnexus.infrastructure.mysql.ops.repository;

import com.graphnexus.infrastructure.mysql.ops.entity.AuditLogDO;
import com.graphnexus.infrastructure.mysql.ops.entity.OperationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作审计日志 Repository — 日志类表，不设逻辑删除。
 *
 * @author Jay
 * @date 2026/06/23
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogDO, Long> {

    long countByOperationTypeAndCreateTimeBetween(
            OperationType type, LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(DISTINCT a.userId) FROM AuditLogDO a WHERE a.createTime BETWEEN :start AND :end")
    long countDistinctUserIdByCreateTimeBetween(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    long countByCreateTimeBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT a.operationType, COUNT(a) FROM AuditLogDO a " +
           "WHERE a.createTime BETWEEN :start AND :end GROUP BY a.operationType")
    List<Object[]> countGroupByOperationTypeBetween(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}