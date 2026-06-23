package com.graphnexus.application.graph.construction.extract;

import com.graphnexus.application.graph.construction.extract.registry.ExtractionEdgeFactoryRegistry;
import com.graphnexus.application.graph.construction.extract.registry.ExtractionNodeHandlerRegistry;
import com.graphnexus.application.graph.construction.extract.registry.ContainsEdgeFactory;
import com.graphnexus.application.graph.construction.extract.registry.DerivesEdgeFactory;
import com.graphnexus.application.graph.construction.extract.registry.ReferencesEdgeFactory;
import com.graphnexus.application.graph.construction.extract.registry.TestNode;
import com.graphnexus.application.graph.construction.extract.registry.TestNodeHandler;
import com.graphnexus.common.LlmGateway;
import com.graphnexus.infrastructure.neo4j.node.GraphNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AC-4 端到端集成测试 — 顶层节点类型扩展路径走完整链路。
 *
 * <p>注册 TestNodeHandler，验证：prompt 段注入 → mock LLM 返回含 testNodes 段的 JSON →
 * 反序列化到 extensionSections → validator 通过 → convertToDomain 的 handler 循环产出 TestNode。
 * 全程未修改 convertToDomain 的核心分支（TestNode 由 handler 循环处理）。</p>
 *
 * @author Jay
 * @date 2026/06/21
 */
@DisplayName("AC-4 顶层节点类型扩展端到端集成测试")
class ExtractionNodeExtensionIntegrationTest {

    @Test
    @DisplayName("TestNode 走完 prompt段→反序列化→校验→转换链路产出领域节点")
    void testNodeFullExtensionPipeline() {
        // 1. 注册 TestNodeHandler 到 registry
        TestNodeHandler testNodeHandler = new TestNodeHandler();
        ExtractionNodeHandlerRegistry nodeHandlerRegistry = new ExtractionNodeHandlerRegistry(List.of());
        nodeHandlerRegistry.register(testNodeHandler);

        // 2. 组装 service（沿用各真实组件 + mock LlmGateway）
        ExtractionPromptBuilder promptBuilder =
                new ExtractionPromptBuilder(new DefaultResourceLoader(), nodeHandlerRegistry, null);
        ExtractionEdgeFactoryRegistry edgeFactoryRegistry =
                new ExtractionEdgeFactoryRegistry(List.of(
                        new DerivesEdgeFactory(), new ContainsEdgeFactory(), new ReferencesEdgeFactory()));
        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.chat(anyString(), anyString())).thenReturn(JSON_WITH_TEST_NODES);

        ExtractionService service = new ExtractionService(llmGateway, promptBuilder,
                new ExtractionValidator(), new ExtractionJsonParser(),
                edgeFactoryRegistry, nodeHandlerRegistry);

        // 3. prompt 段：TestNode 的 promptSchema 应出现在 system prompt（通过 promptBuilder 注入 LLM）
        String systemPrompt = promptBuilder.buildSystemPrompt("数学");
        assertTrue(systemPrompt.contains("testNodes"), "system prompt 应含 TestNode 扩展段 schema");

        // 4. 走完整抽取链路（mock LLM 返回含 testNodes 段的 JSON）
        ExtractionService.ExtractionResult result = service.extract(
                "文本内容", "doc.pdf", "数学", 1, "doc-1");

        // 5. 断言 TestNode 由 handler 循环产出（非核心分支新增）
        List<GraphNode> extensionNodes = result.extensionNodes();
        assertEquals(2, extensionNodes.size(), "TestNode 应由 handler 循环产出 2 个");
        for (GraphNode node : extensionNodes) {
            assertInstanceOf(TestNode.class, node, "扩展节点应为 TestNode 类型");
            assertEquals("doc-1", node.getDocumentId(), "documentId 应正确注入");
            assertEquals(TestNode.NODE_TYPE, node.getNodeType());
        }
        // 核心段不受影响
        assertEquals(1, result.entities().size());
        assertEquals(1, result.knowledgePoints().size());
    }

    @Test
    @DisplayName("TestNode 校验失败（name 为空）→ 阻断抽取，不产出 TestNode")
    void testNodeValidationFailureBlocksPipeline() {
        TestNodeHandler testNodeHandler = new TestNodeHandler();
        ExtractionNodeHandlerRegistry nodeHandlerRegistry = new ExtractionNodeHandlerRegistry(List.of());
        nodeHandlerRegistry.register(testNodeHandler);

        ExtractionPromptBuilder promptBuilder =
                new ExtractionPromptBuilder(new DefaultResourceLoader(), nodeHandlerRegistry, null);
        ExtractionEdgeFactoryRegistry edgeFactoryRegistry =
                new ExtractionEdgeFactoryRegistry(List.of(
                        new DerivesEdgeFactory(), new ContainsEdgeFactory(), new ReferencesEdgeFactory()));
        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.chat(anyString(), anyString())).thenReturn(JSON_WITH_INVALID_TEST_NODE);

        ExtractionService service = new ExtractionService(llmGateway, promptBuilder,
                new ExtractionValidator(), new ExtractionJsonParser(),
                edgeFactoryRegistry, nodeHandlerRegistry);

        com.graphnexus.common.exception.BusinessException ex = assertThrows(
                com.graphnexus.common.exception.BusinessException.class,
                () -> service.extract("文本", "doc", "数学", 1, "doc-1"));
        assertEquals("A0010", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("testNodes"));
    }

    /** 含合法 testNodes 段的 mock LLM 响应 */
    private static final String JSON_WITH_TEST_NODES = """
            {
              "entities": [
                {"entityType":"DEFINITION","name":"e1","originalText":"t1","pageNumber":1}
              ],
              "knowledgePoints": [
                {"name":"k1","description":"d","subject":"数学","gradeLevel":"初中"}
              ],
              "categories": [],
              "alignments": [
                {"entityIndex":0,"knowledgePointIndex":0}
              ],
              "entityRelations": [],
              "prerequisites": [],
              "categoryRelations": [],
              "testNodes": [
                {"name":"tn1","originalText":"片段1"},
                {"name":"tn2","originalText":"片段2"}
              ]
            }""";

    /** testNodes 段含 name 为空的非法项 */
    private static final String JSON_WITH_INVALID_TEST_NODE = """
            {
              "entities": [
                {"entityType":"DEFINITION","name":"e1","originalText":"t1","pageNumber":1}
              ],
              "knowledgePoints": [
                {"name":"k1","description":"d","subject":"数学","gradeLevel":"初中"}
              ],
              "categories": [],
              "alignments": [
                {"entityIndex":0,"knowledgePointIndex":0}
              ],
              "entityRelations": [],
              "prerequisites": [],
              "categoryRelations": [],
              "testNodes": [
                {"name":"","originalText":"x"}
              ]
            }""";
}
