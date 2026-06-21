package com.graphnexus.infrastructure.neo4j.node;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.neo4j.core.schema.Node;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GraphNode 抽象层可扩展性测试（对应 AC-5）。
 *
 * @author Jay
 * @date 2026/06/13
 */
@DisplayName("GraphNode 抽象层可扩展性测试")
class GraphNodeAbstractionTest {

    @Node("TestLabel")
    static class TestNode extends GraphNode {
        private String testField;

        public TestNode(String testField, String documentId) {
            super("TestLabel");
            this.setDocumentId(documentId);
            this.testField = testField;
        }

        public String getTestField() {
            return testField;
        }
    }

    @Test
    @DisplayName("新增子类可正确继承 GraphNode 字段并自动生成 ID")
    void testNewNodeType_InheritsFields() {
        TestNode node = new TestNode("hello", "doc-1");

        assertNotNull(node.getId(), "ID 应自动生成");
        assertEquals("TestLabel", node.getNodeType());
        assertEquals("doc-1", node.getDocumentId());
        assertEquals("hello", node.getTestField());
        assertNotNull(node.getCreatedAt(), "创建时间应自动生成");
        assertNotNull(node.getProperties(), "properties Map 应初始化");
    }

    @Test
    @DisplayName("NodeType 枚举支持 fromLabel 查找")
    void testNodeTypeFromLabel() {
        assertEquals(NodeType.ENTITY, NodeType.fromLabel("Entity"));
        assertEquals(NodeType.KNOWLEDGE_POINT, NodeType.fromLabel("KnowledgePoint"));
        assertEquals(NodeType.DOCUMENT, NodeType.fromLabel("Document"));
        assertNull(NodeType.fromLabel("NonExistent"));
    }

    @Test
    @DisplayName("NodeType 包含全部 6 种已注册类型且 nodeClass 非空")
    void testNodeTypeHasAllRegisteredTypes() {
        NodeType[] types = NodeType.values();
        assertEquals(7, types.length);
        assertTrue(containsLabel(types, "Document"));
        assertTrue(containsLabel(types, "Entity"));
        assertTrue(containsLabel(types, "KnowledgePoint"));
        assertTrue(containsLabel(types, "KnowledgeCategory"));

        // 所有 nodeClass 都不为 null
        for (NodeType t : types) {
            assertNotNull(t.getNodeClass(),
                    t.name() + " 的 nodeClass 不应为 null");
        }
        assertEquals(FileNode.class, NodeType.DOCUMENT.getNodeClass());
        assertEquals(EntityNode.class, NodeType.ENTITY.getNodeClass());
        assertEquals(KnowledgePointNode.class, NodeType.KNOWLEDGE_POINT.getNodeClass());
        assertEquals(KnowledgeCategoryNode.class, NodeType.KNOWLEDGE_CATEGORY.getNodeClass());
    }

    private boolean containsLabel(NodeType[] types, String label) {
        for (NodeType t : types) {
            if (t.getLabel().equals(label)) return true;
        }
        return false;
    }
}