package com.graphnexus.infrastructure.neo4j.edge;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * ALIGNED_TO 边 — EntityNode → KnowledgePointNode。
 *
 * <p>将文档原文片段实体对齐到标准化知识点，是多文档知识融合的关键桥梁。</p>
 *
 * @author Jay
 * @date 2026/06/13
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AlignedToEdge extends GraphEdge {

    public AlignedToEdge(String sourceNodeId, String targetNodeId) {
        super(EdgeType.ALIGNED_TO.getRelationshipType());
        this.setSourceNodeId(sourceNodeId);
        this.setTargetNodeId(targetNodeId);
    }
}