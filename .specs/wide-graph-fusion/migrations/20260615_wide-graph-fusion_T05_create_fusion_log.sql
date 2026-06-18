-- up: 创建 fusion_log 表
-- 设计依据: DESIGN D6 (JSON 列存快照) + D7 (回滚日志) + D9 (并发控制)
CREATE TABLE IF NOT EXISTS fusion_log (
    id                    BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '技术主键',
    trigger_type          VARCHAR(32)  NOT NULL                 COMMENT '触发方式 MANUAL_FULL / AUTO_INCREMENTAL',
    status                VARCHAR(32)  NOT NULL                 COMMENT '融合状态 RUNNING / COMPLETED / ROLLED_BACK',
    merged_kp_group_count INT          NOT NULL DEFAULT 0       COMMENT '融合 KP 组数',
    masters_edge_count    INT          NOT NULL DEFAULT 0       COMMENT '更新的 MASTERS 边数',
    fusion_detail_json    MEDIUMTEXT                            COMMENT '融合明细 JSON [{groupId, sourceKpIds[], targetKpId, redirectedEdges[]}]',
    masters_snapshot_json MEDIUMTEXT                            COMMENT 'MASTERS 变更快照 [{studentNo, kpName, oldWeight, newWeight}]',
    rolled_back           TINYINT      NOT NULL DEFAULT 0       COMMENT '是否已回滚 0=否 1=是',
    executed_at           DATETIME     NOT NULL                 COMMENT '融合执行时间',
    create_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_status (status),
    INDEX idx_executed_at (executed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宽图谱融合操作日志表';

-- down: 删除 fusion_log 表
-- DROP TABLE IF EXISTS fusion_log;