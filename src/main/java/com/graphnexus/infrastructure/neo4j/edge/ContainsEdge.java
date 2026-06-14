package com.graphnexus.infrastructure.neo4j.edge;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * CONTAINS 边 — EntityNode → EntityNode，表示包含关系（A 概念包含 B 子概念）。
 *
 * @author Jay
 * @date 2026/06/13
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ContainsEdge extends GraphEdge {

    public ContainsEdge(String sourceNodeId, String targetNodeId, String description) {
        super(EdgeType.CONTAINS.getRelationshipType());
        this.setSourceNodeId(sourceNodeId);
        this.setTargetNodeId(targetNodeId);
        this.setDescription(description);
    }
}