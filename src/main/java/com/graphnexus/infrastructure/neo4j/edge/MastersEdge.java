package com.graphnexus.infrastructure.neo4j.edge;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * MASTERS 边 — Student → KnowledgePoint 掌握度聚合边。
 *
 * <p>衍生边：由所有 TESTED 路径的得分率经时间衰减加权平均计算得出。
 * weight 为掌握度（0~1），description 存储 JSON 摘要（考试次数/最近考试日期/各次得分率）。
 * 每次融合触发时全量重算覆盖，不独立写入新事实。
 * 见 ADR-007 + DESIGN D5。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MastersEdge extends GraphEdge {

    public MastersEdge(String studentNodeId, String kpNodeId) {
        super(EdgeType.MASTERS.getRelationshipType());
        this.setSourceNodeId(studentNodeId);
        this.setTargetNodeId(kpNodeId);
    }
}