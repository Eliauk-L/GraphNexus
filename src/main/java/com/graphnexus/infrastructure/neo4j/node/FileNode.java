package com.graphnexus.infrastructure.neo4j.node;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Node;

import java.util.Map;

/**
 * Neo4j 文档节点 — 对应已解析的 PDF 文档。
 *
 * <p>学科信息通过 BELONGS_TO_SUBJECT 边关联 SubjectNode（见 ADR-019）。</p>
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

    /** PDF 页数 */
    private Integer pageCount;

    public FileNode(String name, Integer pageCount, String documentId) {
        super(NodeType.DOCUMENT.getLabel());
        this.setDocumentId(documentId);
        this.name = name;
        this.pageCount = pageCount;
    }

    @Override
    public Map<String, Object> toProperties() {
        Map<String, Object> props = super.toProperties();
        props.put("name", this.getName());
        props.put("pageCount", this.getPageCount());
        return props;
    }
}