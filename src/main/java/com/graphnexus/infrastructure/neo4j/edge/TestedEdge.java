package com.graphnexus.infrastructure.neo4j.edge;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * TESTED 边 — Exam → KnowledgePoint。
 *
 * <p>纯结构边（无额外属性），表示某次考试考查了某个知识点。
 * 分数不存边（仅存 MySQL exam_record.score_details）。
 * 见 DESIGN §2.3 + D7。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TestedEdge extends GraphEdge {

    public TestedEdge(String examNodeId, String kpNodeId) {
        super(EdgeType.TESTED.getRelationshipType());
        this.setSourceNodeId(examNodeId);
        this.setTargetNodeId(kpNodeId);
    }
}