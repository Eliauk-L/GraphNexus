package com.graphnexus.infrastructure.neo4j.node;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Node;

import java.util.Map;

/**
 * Neo4j 知识点节点 — 跨文档的标准化学科概念。
 *
 * <p>学科信息已从字符串属性改为 SubjectNode + BELONGS_TO_SUBJECT 边（见 ADR-019）。</p>
 *
 * @author Jay
 * @date 2026/06/13
 */
@Data
@Node("KnowledgePoint")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class KnowledgePointNode extends GraphNode {

    /** 标准知识点名称（如"二次函数顶点坐标"） */
    private String name;

    /** 一句话描述 */
    private String description;

    /** 年级/学段（如"初中"、"高中"） */
    private String gradeLevel;

    /**
     * 融合来源标记 — 记录此 KP 节点的数据来源。
     */
    private String fusionSource;

    /** 文档抽取构造器（文档 KP） */
    public KnowledgePointNode(String name, String description,
                               String gradeLevel, String documentId) {
        super(NodeType.KNOWLEDGE_POINT.getLabel());
        this.setDocumentId(documentId);
        this.name = name;
        this.description = description;
        this.gradeLevel = gradeLevel;
        this.fusionSource = "DOCUMENT";
    }

    /** 成绩导入构造器（考试 KP，不关联 documentId） */
    public KnowledgePointNode(String name) {
        super(NodeType.KNOWLEDGE_POINT.getLabel());
        this.name = name;
        this.fusionSource = "CSV_IMPORT";
    }

    /** 融合规范节点构造器 */
    public KnowledgePointNode(String name, String description,
                               String gradeLevel, String documentId, String fusionSource) {
        super(NodeType.KNOWLEDGE_POINT.getLabel());
        this.setDocumentId(documentId);
        this.name = name;
        this.description = description;
        this.gradeLevel = gradeLevel;
        this.fusionSource = fusionSource;
    }

    @Override
    public Map<String, Object> toProperties() {
        Map<String, Object> props = super.toProperties();
        props.put("name", this.getName());
        props.put("description", this.getDescription());
        props.put("gradeLevel", this.getGradeLevel());
        props.put("fusionSource", this.getFusionSource());
        return props;
    }
}