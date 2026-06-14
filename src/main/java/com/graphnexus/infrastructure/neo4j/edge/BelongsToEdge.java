package com.graphnexus.infrastructure.neo4j.edge;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * BELONGS_TO 边 — KnowledgePointNode → KnowledgeCategoryNode。
 *
 * <p>表示知识点归属于某个知识分类。</p>
 *
 * @author Jay
 * @date 2026/06/13
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BelongsToEdge extends GraphEdge {

    public BelongsToEdge(String sourceNodeId, String targetNodeId) {
        super(EdgeType.BELONGS_TO.getRelationshipType());
        this.setSourceNodeId(sourceNodeId);
        this.setTargetNodeId(targetNodeId);
    }
}