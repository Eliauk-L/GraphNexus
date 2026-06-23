package com.graphnexus.infrastructure.mysql.ops.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 统计快照实体（JPA Entity），对应 MySQL {@code stats_snapshot} 表。
 *
 * <p>每日凌晨定时任务采集并存储关键统计指标的聚合值。
 * snapshot_data 为 JSON 列，存储三层嵌套的完整快照数据
 * （usage / documents / graph + 按学科明细）。
 * 为运营仪表盘的历史趋势图表提供数据源。</p>
 *
 * @author Jay
 * @date 2026/06/23
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "stats_snapshot")
@EntityListeners(AuditingEntityListener.class)
public class StatsSnapshotDO {

    /** 技术主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 快照日期（唯一约束，每日仅一条） */
    @Column(name = "snapshot_date", nullable = false, unique = true)
    private LocalDate snapshotDate;

    /** 快照数据 JSON（usage + documents + graph 三层嵌套，见 ADR-052） */
    @Column(name = "snapshot_data", columnDefinition = "JSON", nullable = false)
    private String snapshotData;

    /** 快照状态：COMPLETED / PARTIAL / FAILED */
    @Column(name = "status", length = 16, nullable = false)
    private String status;

    /** 失败原因（PARTIAL/FAILED 时填写） */
    @Column(name = "fail_reason", length = 512)
    private String failReason;

    /** 创建时间（自动填充） */
    @CreatedDate
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;
}