package com.graphnexus.infrastructure.mysql.ops.repository;

import com.graphnexus.infrastructure.mysql.ops.entity.AuditLogDO;
import com.graphnexus.infrastructure.mysql.ops.entity.OperationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * 操作审计日志 Repository — 日志类表，不设逻辑删除。
 *
 * @author Jay
 * @date 2026/06/23
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogDO, Long> {

    /**
     * 统计指定时间范围内某操作类型的记录数。
     *
     * @param type  操作类型
     * @param start 开始时间（含）
     * @param end   结束时间（不含）
     * @return 记录数
     */
    long countByOperationTypeAndCreateTimeBetween(
            OperationType type, LocalDateTime start, LocalDateTime end);

    /**
     * 统计指定时间范围内的独立活跃用户数。
     *
     * @param start 开始时间（含）
     * @param end   结束时间（不含）
     * @return 独立用户数
     */
    @Query("SELECT COUNT(DISTINCT a.userId) FROM AuditLogDO a WHERE a.createTime BETWEEN :start AND :end")
    long countDistinctUserIdByCreateTimeBetween(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * 统计指定时间范围内的总操作次数。
     *
     * @param start 开始时间（含）
     * @param end   结束时间（不含）
     * @return 总操作次数
     */
    long countByCreateTimeBetween(LocalDateTime start, LocalDateTime end);

    /**
     * 按操作类型分组统计指定时间范围内的操作次数。
     *
     * @param start 开始时间（含）
     * @param end   结束时间（不含）
     * @return Object[] 数组：{operationType, count}
     */
    @Query("SELECT a.operationType, COUNT(a) FROM AuditLogDO a " +
           "WHERE a.createTime BETWEEN :start AND :end GROUP BY a.operationType")
    java.util.List<Object[]> countGroupByOperationTypeBetween(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}