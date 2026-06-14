package com.graphnexus.infrastructure.neo4j.edge;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 实体间引用边 — EntityNode → EntityNode。
 *
 * <p>通过 {@code referenceType} 字段区分三种语义：DERIVES（推导）/ CONTAINS（包含）/ REFERENCES（引用）。
 * 三种逻辑边类型合并为一个 Java 类，减少类数量。</p>
 *
 * @author Jay
 * @date 2026/06/13
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReferencesEdge extends GraphEdge {

    /**
     * 细分类型：DERIVES / CONTAINS / REFERENCES。
     * 对应 EdgeType 枚举中 DERIVES、CONTAINS、REFERENCES 的 relationshipType。
     */
    private String referenceType;

    /** 关系说明（如"一般式通过配方可推导出对称轴公式"） */
    private String description;

    public ReferencesEdge(String sourceNodeId, String targetNodeId, String referenceType, String description) {
        super(referenceType);
        this.setSourceNodeId(sourceNodeId);
        this.setTargetNodeId(targetNodeId);
        this.referenceType = referenceType;
        this.description = description;
    }
}