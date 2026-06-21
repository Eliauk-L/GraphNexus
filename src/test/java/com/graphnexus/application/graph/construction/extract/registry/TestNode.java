package com.graphnexus.application.graph.construction.extract.registry;

import com.graphnexus.infrastructure.neo4j.node.GraphNode;

/**
 * 测试用扩展节点 — extends GraphNode 的最小子类，验证 ExtractionNodeHandler 扩展路径（D4 / AC-4）。
 *
 * <p>仅测试用途，不标注 @Node、不参与持久化。</p>
 *
 * @author Jay
 * @date 2026/06/21
 */
public class TestNode extends GraphNode {

    /** 测试节点 label（仅供测试识别） */
    public static final String NODE_TYPE = "Test";

    private String name;
    private String originalText;

    public TestNode() {
        super(NODE_TYPE);
    }

    public TestNode(String name, String originalText, String documentId) {
        super(NODE_TYPE);
        this.name = name;
        this.originalText = originalText;
        this.setDocumentId(documentId);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOriginalText() {
        return originalText;
    }

    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }
}
