package com.graphnexus.infrastructure.neo4j.node;

import lombok.Getter;

/**
 * 图节点类型注册枚举 — 集中管理所有 Neo4j 节点类型的 label 和对应的 Java 类。
 *
 * <p><b>扩展方式</b>：新增节点类型只需在此枚举加一行即可，无需修改 Repository 或 Service 代码。
 * 例：{@code QUESTION("Question", QuestionNode.class)}</p>
 *
 * @author Jay
 * @date 2026/06/13
 * @see GraphNode
 */
@Getter
public enum NodeType {

    /** 文档节点 */
    DOCUMENT("Document", DocumentNode.class),

    /** 实体节点（从文档原文中抽取的片段） */
    ENTITY("Entity", EntityNode.class),

    /** 知识点节点（跨文档标准化概念） */
    KNOWLEDGE_POINT("KnowledgePoint", KnowledgePointNode.class),

    /** 知识分类节点（层次分类树中的节点） */
    KNOWLEDGE_CATEGORY("KnowledgeCategory", KnowledgeCategoryNode.class),

    /** 学生节点（CSV 成绩导入） */
    STUDENT("Student", StudentNode.class),

    /** 考试节点（CSV 成绩导入） */
    EXAM("Exam", ExamNode.class);

    /**
     * Neo4j label 名称（如 {@code "Entity"}、{@code "KnowledgePoint"}）。
     */
    private final String label;

    /**
     * 对应的 Java 节点子类，可用于反射实例化或运行时类型校验。
     */
    private final Class<? extends GraphNode> nodeClass;

    NodeType(String label, Class<? extends GraphNode> nodeClass) {
        this.label = label;
        this.nodeClass = nodeClass;
    }

    /**
     * 根据 label 字符串查找对应的节点类型。
     *
     * @param label Neo4j label 名称
     * @return 匹配的 NodeType，未找到返回 {@code null}
     */
    public static NodeType fromLabel(String label) {
        for (NodeType type : values()) {
            if (type.label.equalsIgnoreCase(label)) {
                return type;
            }
        }
        return null;
    }
}