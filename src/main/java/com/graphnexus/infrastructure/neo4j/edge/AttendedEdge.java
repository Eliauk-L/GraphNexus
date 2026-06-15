package com.graphnexus.infrastructure.neo4j.edge;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * ATTENDED 边 — Student → Exam。
 *
 * <p>纯结构边（无额外属性），表示学生参加了某次考试。
 * 见 DESIGN §2.3。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AttendedEdge extends GraphEdge {

    public AttendedEdge(String studentNodeId, String examNodeId) {
        super(EdgeType.ATTENDED.getRelationshipType());
        this.setSourceNodeId(studentNodeId);
        this.setTargetNodeId(examNodeId);
    }
}