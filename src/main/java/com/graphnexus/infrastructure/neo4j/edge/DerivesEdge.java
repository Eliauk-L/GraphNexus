package com.graphnexus.infrastructure.neo4j.edge;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * DERIVES 边 — EntityNode → EntityNode，表示推导关系（A 可推导出 B）。
 *
 * @author Jay
 * @date 2026/06/13
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DerivesEdge extends GraphEdge {

    public DerivesEdge(String sourceNodeId, String targetNodeId, String description) {
        super(EdgeType.DERIVES.getRelationshipType());
        this.setSourceNodeId(sourceNodeId);
        this.setTargetNodeId(targetNodeId);
        this.setDescription(description);
    }
}