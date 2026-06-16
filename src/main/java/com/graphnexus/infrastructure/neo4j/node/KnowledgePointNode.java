package com.graphnexus.infrastructure.neo4j.node;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Node;

import java.util.Map;

/**
 * Neo4j 知识点节点 — 跨文档的标准化学科概念。
 *
 * <p>多个文档的 EntityNode 可以通过 ALIGNED_TO 边对齐到同一个 KnowledgePointNode。
 * 知识点之间通过 PREREQUISITE_OF 边建立前置依赖关系。</p>
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

    /** 学科（继承自源文档，如"数学"） */
    private String subject;

    /** 年级/学段（如"初中"、"高中"） */
    private String gradeLevel;

    /**
     * 融合来源标记 — 记录此 KP 节点的数据来源。
     *
     * <p>可能值：{@code "DOCUMENT"}（文档抽取）、{@code "CSV_IMPORT"}（成绩导入）、
     * {@code "DOCUMENT,CSV_IMPORT"}（融合后的规范节点，来自多源）。
     * 见 DESIGN D10。</p>
     */
    private String fusionSource;

    public KnowledgePointNode(String name, String description, String subject,
                               String gradeLevel, String documentId) {
        super(NodeType.KNOWLEDGE_POINT.getLabel());
        this.setDocumentId(documentId);
        this.name = name;
        this.description = description;
        this.subject = subject;
        this.gradeLevel = gradeLevel;
        this.fusionSource = "DOCUMENT";
    }

    /**
     * CSV 成绩解析专用构造器 — 不关联 documentId。
     *
     * <p>CSV 成绩文件不是 Document，其中的知识点节点独立存在，
     * 通过 {@code name + subject} 做 MERGE key 自动去重。见 DESIGN D6。</p>
     */
    public KnowledgePointNode(String name, String subject) {
        super(NodeType.KNOWLEDGE_POINT.getLabel());
        this.name = name;
        this.subject = subject;
        this.fusionSource = "CSV_IMPORT";
    }

    /**
     * 融合规范节点构造器 — 继承主 KP 信息并标记多源。
     */
    public KnowledgePointNode(String name, String description, String subject,
                               String gradeLevel, String documentId, String fusionSource) {
        super(NodeType.KNOWLEDGE_POINT.getLabel());
        this.setDocumentId(documentId);
        this.name = name;
        this.description = description;
        this.subject = subject;
        this.gradeLevel = gradeLevel;
        this.fusionSource = fusionSource;
    }

    @Override
    public Map<String, Object> toProperties() {
        Map<String, Object> props = super.toProperties();
        props.put("name", this.getName());
        props.put("description", this.getDescription());
        props.put("subject", this.getSubject());
        props.put("gradeLevel", this.getGradeLevel());
        props.put("fusionSource", this.getFusionSource());
        return props;
    }
}