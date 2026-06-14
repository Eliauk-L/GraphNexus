package com.graphnexus.infrastructure.neo4j.node;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Node;

/**
 * Neo4j 知识分类节点 — 知识点的层次分类树节点。
 *
 * <p>通过 CHILD_OF 边形成树状结构（如 初中数学→代数→函数→二次函数）。
 * KnowledgePointNode 通过 BELONGS_TO 边归属于叶子分类节点。</p>
 *
 * @author Jay
 * @date 2026/06/13
 */
@Data
@Node("KnowledgeCategory")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class KnowledgeCategoryNode extends GraphNode {

    /** 分类名称（如"二次函数"、"函数"、"代数"） */
    private String name;

    /** 层级深度（1=根节点，逐层递增） */
    private Integer level;

    /** 父分类名称（LLM 输出中用于关联，CHILD_OF 边单独表达） */
    private String parentName;

    public KnowledgeCategoryNode(String name, Integer level, String parentName, String documentId) {
        super(NodeType.KNOWLEDGE_CATEGORY.getLabel());
        this.setDocumentId(documentId);
        this.name = name;
        this.level = level;
        this.parentName = parentName;
    }
}