package com.graphnexus.infrastructure.neo4j.edge;

import lombok.Getter;

/**
 * 图关系边类型注册枚举 — 集中管理所有 Neo4j 边类型的名称、方向和中文说明。
 *
 * @author Jay
 * @date 2026/06/13
 */
@Getter
public enum EdgeType {

    /** 文档抽取实体：FileNode → EntityNode */
    EXTRACTS("EXTRACTS", "文档抽取"),

    /** 实体间引用（含 DERIVES / CONTAINS 语义） */
    REFERENCES("REFERENCES", "实体引用"),

    /** 实体间推导关系 */
    DERIVES("DERIVES", "推导关系"),

    /** 实体间包含关系 */
    CONTAINS("CONTAINS", "包含关系"),

    /** 实体对齐到知识点：EntityNode → KnowledgePointNode */
    ALIGNED_TO("ALIGNED_TO", "实体对齐"),

    /** 知识点归属分类：KnowledgePointNode → KnowledgeCategoryNode */
    BELONGS_TO("BELONGS_TO", "分类归属"),

    /** 分类层次关系：子分类 → 父分类 */
    CHILD_OF("CHILD_OF", "分类层级"),

    /** 知识点前置依赖：前置 KnowledgePoint → 后置 KnowledgePoint */
    PREREQUISITE_OF("PREREQUISITE_OF", "前置依赖"),

    /** 学生参加考试：Student → Exam */
    ATTENDED("ATTENDED", "学生参加"),

    /** 考试考查知识点：Exam → KnowledgePoint */
    TESTED("TESTED", "考试考查"),

    /** 学生掌握度：Student → KnowledgePoint（聚合边） */
    MASTERS("MASTERS", "学生掌握度"),

    /** 学科归属：KnowledgePoint|Exam|FileNode → SubjectNode */
    BELONGS_TO_SUBJECT("BELONGS_TO_SUBJECT", "学科归属");

    /** Neo4j relationship type 名称 */
    private final String relationshipType;

    /** 前端展示用的中文说明 */
    private final String displayName;

    EdgeType(String relationshipType, String displayName) {
        this.relationshipType = relationshipType;
        this.displayName = displayName;
    }

    /** 根据字符串查找对应的边类型 */
    public static EdgeType fromType(String type) {
        for (EdgeType edgeType : values()) {
            if (edgeType.relationshipType.equalsIgnoreCase(type)) {
                return edgeType;
            }
        }
        return null;
    }
}