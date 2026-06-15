package com.graphnexus.infrastructure.neo4j.edge;

import lombok.Getter;

/**
 * 图关系边类型注册枚举 — 集中管理所有 Neo4j 边类型的名称和方向。
 *
 * <p><b>扩展方式</b>：新增边类型只需在此枚举加一行即可，无需修改 Repository 或 Service 代码。
 * 例：{@code MASTERS("MASTERS")}</p>
 *
 * @author Jay
 * @date 2026/06/13
 * @see GraphEdge
 */
@Getter
public enum EdgeType {

    /** 文档抽取实体：DocumentNode → EntityNode */
    EXTRACTS("EXTRACTS"),

    /** 实体间引用（含 DERIVES / CONTAINS 语义，通过 ReferencesEdge.referenceType 字段区分） */
    REFERENCES("REFERENCES"),

    /** 实体间推导关系 */
    DERIVES("DERIVES"),

    /** 实体间包含关系 */
    CONTAINS("CONTAINS"),

    /** 实体对齐到知识点：EntityNode → KnowledgePointNode */
    ALIGNED_TO("ALIGNED_TO"),

    /** 知识点归属分类：KnowledgePointNode → KnowledgeCategoryNode */
    BELONGS_TO("BELONGS_TO"),

    /** 分类层次关系：子分类 → 父分类 */
    CHILD_OF("CHILD_OF"),

    /** 知识点前置依赖：前置 KnowledgePoint → 后置 KnowledgePoint */
    PREREQUISITE_OF("PREREQUISITE_OF"),

    /** 学生参加考试：Student → Exam */
    ATTENDED("ATTENDED"),

    /** 考试考查知识点：Exam → KnowledgePoint */
    TESTED("TESTED");

    /**
     * Neo4j relationship type 名称（如 {@code "EXTRACTS"}、{@code "PREREQUISITE_OF"}）。
     */
    private final String relationshipType;

    EdgeType(String relationshipType) {
        this.relationshipType = relationshipType;
    }

    /**
     * 根据字符串查找对应的边类型。
     */
    public static EdgeType fromType(String type) {
        for (EdgeType edgeType : values()) {
            if (edgeType.relationshipType.equalsIgnoreCase(type)) {
                return edgeType;
            }
        }
        return null;
    }
}