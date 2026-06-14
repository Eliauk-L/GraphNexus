package com.graphnexus.infrastructure.neo4j.edge;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * PREREQUISITE_OF 边 — KnowledgePointNode → KnowledgePointNode。
 *
 * <p>表示学习 B 知识点前必须先掌握 A 知识点。
 * 例：对称轴 →(PREREQUISITE_OF 0.95)→ 顶点坐标。</p>
 *
 * @author Jay
 * @date 2026/06/13
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PrerequisiteEdge extends GraphEdge {

    /** 依赖强度（0~1），越高表示越强依赖 */
    private Double strength;

    /** 为什么 B 依赖 A 的说明 */
    private String description;

    public PrerequisiteEdge(String sourceNodeId, String targetNodeId, Double strength, String description) {
        super(EdgeType.PREREQUISITE_OF.getRelationshipType());
        this.setSourceNodeId(sourceNodeId);
        this.setTargetNodeId(targetNodeId);
        this.strength = strength;
        this.description = description;
    }
}