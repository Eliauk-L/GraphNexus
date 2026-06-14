package com.graphnexus.infrastructure.neo4j.node;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Node;

import java.util.Map;

/**
 * Neo4j 实体节点 — LLM 从文档原文中抽取的知识片段。
 *
 * <p>EntityNode 生命周期绑定源文档，保留原文出处（书名、页码、原文措辞），
 * 通过 ALIGNED_TO 边对接到跨文档的标准 KnowledgePointNode。</p>
 *
 * @author Jay
 * @date 2026/06/13
 */
@Data
@Node("Entity")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EntityNode extends GraphNode {

    /** 实体类型枚举：DEFINITION / FORMULA / CONCEPT / EXAMPLE / SOLUTION */
    private String entityType;

    /** 简洁名称（≤ 50 字），如"二次函数定义" */
    private String name;

    /** 原文摘录片段（≤ 500 字） */
    private String originalText;

    /** 所在 PDF 页码 */
    private Integer pageNumber;

    /** 可选的扩展元数据（LLM 额外提取的信息） */
    private Map<String, Object> metadata;

    public EntityNode(String entityType, String name, String originalText, Integer pageNumber,
                      String documentId, Map<String, Object> metadata) {
        super(NodeType.ENTITY.getLabel());
        this.setDocumentId(documentId);
        this.entityType = entityType;
        this.name = name;
        this.originalText = originalText;
        this.pageNumber = pageNumber;
        this.metadata = metadata;
    }
}