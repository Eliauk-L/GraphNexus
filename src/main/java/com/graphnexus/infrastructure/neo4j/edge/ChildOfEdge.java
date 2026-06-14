package com.graphnexus.infrastructure.neo4j.edge;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * CHILD_OF 边 — KnowledgeCategoryNode → KnowledgeCategoryNode（子 → 父）。
 *
 * <p>将知识点分类节点连接成层次树（如 二次函数→函数→代数→初中数学）。</p>
 *
 * @author Jay
 * @date 2026/06/13
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ChildOfEdge extends GraphEdge {

    public ChildOfEdge(String sourceNodeId, String targetNodeId) {
        super(EdgeType.CHILD_OF.getRelationshipType());
        this.setSourceNodeId(sourceNodeId);
        this.setTargetNodeId(targetNodeId);
    }
}