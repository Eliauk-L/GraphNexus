package com.graphnexus.infrastructure.neo4j.edge;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * EXTRACTS 边 — FileNode → EntityNode。
 *
 * <p>表示实体是从该文档中抽取出来的。</p>
 *
 * @author Jay
 * @date 2026/06/13
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ExtractsEdge extends GraphEdge {

    public ExtractsEdge(String sourceNodeId, String targetNodeId) {
        super(EdgeType.EXTRACTS.getRelationshipType());
        this.setSourceNodeId(sourceNodeId);
        this.setTargetNodeId(targetNodeId);
    }
}