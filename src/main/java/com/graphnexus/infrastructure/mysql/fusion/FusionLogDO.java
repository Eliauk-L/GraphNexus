package com.graphnexus.infrastructure.mysql.fusion;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 融合操作日志实体（JPA Entity），对应 MySQL {@code fusion_log} 表。
 *
 * <p>记录每次融合操作的完整审计信息，含融合明细 JSON 和 MASTERS 变更快照 JSON，
 * 支持基于日志的精确回滚。见 ADR-008 + DESIGN D6/D7。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "fusion_log")
@EntityListeners(AuditingEntityListener.class)
public class FusionLogDO {

    /** 技术主键 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 触发方式：MANUAL_FULL / AUTO_INCREMENTAL */
    @Column(name = "trigger_type", length = 32, nullable = false)
    private String triggerType;

    /** 融合状态：RUNNING / COMPLETED / ROLLED_BACK */
    @Column(name = "status", length = 32, nullable = false)
    private String status;

    /** 融合 KP 组数 */
    @Column(name = "merged_kp_group_count")
    private int mergedKpGroupCount;

    /** 更新的 MASTERS 边数 */
    @Column(name = "masters_edge_count")
    private int mastersEdgeCount;

    /**
     * 融合明细 JSON（MEDIUMTEXT）— 每组含源 KP ID 列表 → 目标 KP ID + 边重定向清单。
     * JSON schema 见 ADR-008 § Decision。
     */
    @Column(name = "fusion_detail_json", columnDefinition = "MEDIUMTEXT")
    private String fusionDetailJson;

    /**
     * MASTERS 变更快照 JSON（MEDIUMTEXT）— 每条含 studentNo/kpName/oldWeight/newWeight。
     * oldWeight 为 null 表示融合前不存在该 MASTERS 边。
     */
    @Column(name = "masters_snapshot_json", columnDefinition = "MEDIUMTEXT")
    private String mastersSnapshotJson;

    /** 是否已回滚 */
    @Column(name = "rolled_back", nullable = false)
    @Builder.Default
    private Boolean rolledBack = false;

    /** 融合执行时间 */
    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    /** 创建时间（自动填充） */
    @CreatedDate
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    /** 更新时间（自动填充） */
    @LastModifiedDate
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;
}