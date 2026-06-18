package com.graphnexus.infrastructure.neo4j.node;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Node;

import java.util.Map;

/**
 * Neo4j 文档节点 — 对应已解析的 PDF 文档。
 *
 * <p>每个通过 {@code document-process-pdf-minimal} 处理过的文档在 Neo4j 中有一个对应的
 * FileNode，通过 EXTRACTS 边连接到从该文档中抽取出的 EntityNode。</p>
 *
 * @author Jay
 * @date 2026/06/13
 */
@Data
@Node("Document")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class FileNode extends GraphNode {

    /** 文档名称 */
    private String name;

    /** 学科（如"数学"、"物理"） */
    private String subject;

    /** PDF 页数 */
    private Integer pageCount;

    public FileNode(String name, String subject, Integer pageCount, String documentId) {
        super(NodeType.DOCUMENT.getLabel());
        this.setDocumentId(documentId);
        this.name = name;
        this.subject = subject;
        this.pageCount = pageCount;
    }

    @Override
    public Map<String, Object> toProperties() {
        Map<String, Object> props = super.toProperties();
        props.put("name", this.getName());
        props.put("subject", this.getSubject());
        props.put("pageCount", this.getPageCount());
        return props;
    }
}