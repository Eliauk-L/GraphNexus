package com.graphnexus.infrastructure.neo4j.edge;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Neo4j 学科归属关系边 — (KnowledgePoint|Exam|FileNode)-[:BELONGS_TO_SUBJECT]->(:Subject)。
 *
 * <p>纯结构边，无额外属性。方向：源节点指向 SubjectNode。
 * 融合分组基于此边确定同一学科下的所有 KP。</p>
 *
 * @author Jay
 * @date 2026/06/20
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BelongsToSubjectEdge extends GraphEdge {

    public BelongsToSubjectEdge(String sourceNodeId, String targetNodeId) {
        super(EdgeType.BELONGS_TO_SUBJECT.getRelationshipType());
        this.setSourceNodeId(sourceNodeId);
        this.setTargetNodeId(targetNodeId);
    }
}