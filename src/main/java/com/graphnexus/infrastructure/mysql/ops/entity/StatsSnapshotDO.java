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
 * snapshot_data 为 JSON 列，为运营仪表盘的历史趋势图表提供数据源。</p>
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_date", nullable = false, unique = true)
    private LocalDate snapshotDate;

    @Column(name = "snapshot_data", columnDefinition = "JSON", nullable = false)
    private String snapshotData;

    @Column(name = "status", length = 16, nullable = false)
    private String status;

    @Column(name = "fail_reason", length = 512)
    private String failReason;

    @CreatedDate
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;
}