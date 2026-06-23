package com.graphnexus.application.graph.construction.extract;

import com.graphnexus.application.graph.construction.extract.registry.EntityRelationType;
import com.graphnexus.application.graph.construction.extract.registry.ExtractionEdgeFactoryRegistry;
import com.graphnexus.application.graph.construction.extract.registry.ExtractionNodeHandler;
import com.graphnexus.application.graph.construction.extract.registry.ExtractionNodeHandlerRegistry;
import com.graphnexus.application.graph.construction.model.ExtractionRawResult;
import com.graphnexus.common.LlmGateway;
import com.graphnexus.infrastructure.neo4j.edge.ContainsEdge;
import com.graphnexus.infrastructure.neo4j.edge.DerivesEdge;
import com.graphnexus.infrastructure.neo4j.edge.GraphEdge;
import com.graphnexus.infrastructure.neo4j.edge.ReferencesEdge;
import com.graphnexus.infrastructure.neo4j.node.GraphNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ExtractionService.convertToDomain 测试 — 对应 AC-3（关系工厂化无 switch）+ AC-4（扩展节点循环）。
 *
 * <p>聚焦 convertToDomain，用 mock LlmGateway 喂固定 JSON；invoke {@code extract(...)} 走全链路。</p>
 *
 * @author Jay
 * @date 2026/06/21
 */
@DisplayName("ExtractionService convertToDomain 测试")
class ExtractionServiceTest {

    private ExtractionService service;
    private ExtractionNodeHandlerRegistry nodeHandlerRegistry;

    @BeforeEach
    void setUp() {
        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.chat(anyString(), anyString())).thenReturn(SAMPLE_JSON);

        ExtractionJsonParser parser = new ExtractionJsonParser();
        ExtractionValidator validator = new ExtractionValidator();
        ExtractionPromptBuilder promptBuilder =
                new ExtractionPromptBuilder(new DefaultResourceLoader(),
                        new ExtractionNodeHandlerRegistry(List.of()), null);
        ExtractionEdgeFactoryRegistry edgeFactoryRegistry =
                new ExtractionEdgeFactoryRegistry(List.of(
                        new com.graphnexus.application.graph.construction.extract.registry.DerivesEdgeFactory(),
                        new com.graphnexus.application.graph.construction.extract.registry.ContainsEdgeFactory(),
                        new com.graphnexus.application.graph.construction.extract.registry.ReferencesEdgeFactory()));
        nodeHandlerRegistry = new ExtractionNodeHandlerRegistry(List.of());

        service = new ExtractionService(llmGateway, promptBuilder, validator, parser,
                edgeFactoryRegistry, nodeHandlerRegistry);
    }

    @Test
    @DisplayName("AC-3：三种关系类型经工厂路由到正确边类（无 switch）")
    void relationTypesRoutedViaFactory() {
        ExtractionService.ExtractionResult result = service.extract(
                "text", "doc", "数学", 1, "doc-1");

        // SAMPLE_JSON 含 4 条 entityRelations：DERIVES×2 + CONTAINS×1 + REFERENCES×1
        boolean hasDerives = result.edges().stream().anyMatch(e -> e instanceof DerivesEdge);
        boolean hasContains = result.edges().stream().anyMatch(e -> e instanceof ContainsEdge);
        boolean hasReferences = result.edges().stream().anyMatch(e -> e instanceof ReferencesEdge);
        assertTrue(hasDerives, "应含 DerivesEdge");
        assertTrue(hasContains, "应含 ContainsEdge");
        assertTrue(hasReferences, "应含 ReferencesEdge");
    }

    @Test
    @DisplayName("AC-3：数据驱动遍历 EntityRelationType × 工厂 — 每类型可路由（无 switch 隐式断言）")
    void allRelationTypesRoutable() {
        for (EntityRelationType type : EntityRelationType.values()) {
            String json = SAMPLE_JSON.replace("\"type\":\"DERIVES\",\"description\":\"定义推导出一般式表达式\"",
                    "\"type\":\"" + type.getValue() + "\",\"description\":\"t\"");
            LlmGateway llm = mock(LlmGateway.class);
            when(llm.chat(anyString(), anyString())).thenReturn(json);
            ExtractionService svc = new ExtractionService(llm,
                    new ExtractionPromptBuilder(new DefaultResourceLoader(), nodeHandlerRegistry, null),
                    new ExtractionValidator(), new ExtractionJsonParser(),
                    new ExtractionEdgeFactoryRegistry(List.of(
                            new com.graphnexus.application.graph.construction.extract.registry.DerivesEdgeFactory(),
                            new com.graphnexus.application.graph.construction.extract.registry.ContainsEdgeFactory(),
                            new com.graphnexus.application.graph.construction.extract.registry.ReferencesEdgeFactory())),
                    nodeHandlerRegistry);

            ExtractionService.ExtractionResult r = svc.extract("text", "doc", "数学", 1, "doc-1");
            assertTrue(r.edges().stream().anyMatch(e -> e.getEdgeType().equals(type.getValue())),
                    "关系类型 " + type + " 未路由到边");
        }
    }

    @Test
    @DisplayName("AC-4：扩展段经 handler 循环产出节点（inline stub handler，非新分支）")
    void extensionSectionProcessedViaHandler() {
        // 注册一个 inline stub handler 到 registry
        StubNodeHandler stub = new StubNodeHandler();
        nodeHandlerRegistry.register(stub);

        // SAMPLE_JSON_EXT 含 testNodes 段
        LlmGateway llm = mock(LlmGateway.class);
        when(llm.chat(anyString(), anyString())).thenReturn(SAMPLE_JSON_EXT);
        ExtractionService svc = new ExtractionService(llm,
                new ExtractionPromptBuilder(new DefaultResourceLoader(), nodeHandlerRegistry, null),
                new ExtractionValidator(), new ExtractionJsonParser(),
                new ExtractionEdgeFactoryRegistry(List.of(
                        new com.graphnexus.application.graph.construction.extract.registry.DerivesEdgeFactory(),
                        new com.graphnexus.application.graph.construction.extract.registry.ContainsEdgeFactory(),
                        new com.graphnexus.application.graph.construction.extract.registry.ReferencesEdgeFactory())),
                nodeHandlerRegistry);

        ExtractionService.ExtractionResult r = svc.extract("text", "doc", "数学", 1, "doc-1");
        assertEquals(2, r.extensionNodes().size(), "扩展节点应由 handler 循环产出");
        assertTrue(stub.validated, "handler.validate 应被调用");
        assertTrue(stub.converted, "handler.convert 应被调用");
    }

    @Test
    @DisplayName("AC-1 回归：核心段产出结构与重构前等价")
    void coreSectionsEquivalent() {
        ExtractionService.ExtractionResult r = service.extract("text", "doc", "数学", 1, "doc-1");
        assertEquals(7, r.entities().size(), "实体数");
        assertEquals(7, r.knowledgePoints().size(), "知识点数");
        assertEquals(4, r.categories().size(), "分类数");
        assertTrue(r.edges().size() >= 4, "边数应含实体关系边");
        assertTrue(r.extensionNodes().isEmpty(), "无注册 handler 时扩展节点为空");
    }

    // ======================== 测试用 inline stub handler ========================

    /** 测试用扩展节点领域类 */
    static class StubNode extends GraphNode {
        public static final String TYPE = "Stub";
        StubNode(String name, String docId) {
            super(TYPE);
            setDocumentId(docId);
        }
    }

    /** 测试用 Raw POJO */
    static class StubNodeRaw {
        @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    /** inline stub handler — 验证扩展循环机制，非 TestNode（避免依赖 T08） */
    static class StubNodeHandler implements ExtractionNodeHandler<StubNodeRaw, StubNode> {
        boolean validated;
        boolean converted;

        @Override
        public String sectionKey() { return "testNodes"; }

        @Override
        public String promptSchema() { return "## 扩展节点（testNodes）"; }

        @Override
        public Class<StubNodeRaw> rawType() { return StubNodeRaw.class; }

        @Override
        public void validate(List<StubNodeRaw> raw, ExtractionRawResult context) {
            validated = true;
            for (int i = 0; i < raw.size(); i++) {
                assertNotNull(raw.get(i).getName(), "testNodes[" + i + "].name 为空");
            }
        }

        @Override
        public List<StubNode> convert(List<StubNodeRaw> raw, String documentId) {
            converted = true;
            List<StubNode> nodes = new ArrayList<>();
            for (StubNodeRaw r : raw) {
                nodes.add(new StubNode(r.getName(), documentId));
            }
            return nodes;
        }
    }

    // ======================== 固定 JSON 样本 ========================

    private static final String SAMPLE_JSON = """
            {
              "entities": [
                {"entityType":"DEFINITION","name":"e1","originalText":"t1","pageNumber":1},
                {"entityType":"FORMULA","name":"e2","originalText":"t2","pageNumber":1},
                {"entityType":"CONCEPT","name":"e3","originalText":"t3","pageNumber":1},
                {"entityType":"EXAMPLE","name":"e4","originalText":"t4","pageNumber":1},
                {"entityType":"SOLUTION","name":"e5","originalText":"t5","pageNumber":1},
                {"entityType":"CONCEPT","name":"e6","originalText":"t6","pageNumber":1},
                {"entityType":"SOLUTION","name":"e7","originalText":"t7","pageNumber":1}
              ],
              "knowledgePoints": [
                {"name":"k1","description":"d","subject":"数学","gradeLevel":"初中"},
                {"name":"k2","description":"d","subject":"数学","gradeLevel":"初中"},
                {"name":"k3","description":"d","subject":"数学","gradeLevel":"初中"},
                {"name":"k4","description":"d","subject":"数学","gradeLevel":"初中"},
                {"name":"k5","description":"d","subject":"数学","gradeLevel":"初中"},
                {"name":"k6","description":"d","subject":"数学","gradeLevel":"初中"},
                {"name":"k7","description":"d","subject":"数学","gradeLevel":"初中"}
              ],
              "categories": [
                {"name":"初中数学","parentName":null,"level":1},
                {"name":"代数","parentName":"初中数学","level":2},
                {"name":"函数","parentName":"代数","level":3},
                {"name":"二次函数","parentName":"函数","level":4}
              ],
              "alignments": [
                {"entityIndex":0,"knowledgePointIndex":0},
                {"entityIndex":1,"knowledgePointIndex":1},
                {"entityIndex":2,"knowledgePointIndex":2},
                {"entityIndex":3,"knowledgePointIndex":3},
                {"entityIndex":4,"knowledgePointIndex":4},
                {"entityIndex":5,"knowledgePointIndex":5},
                {"entityIndex":6,"knowledgePointIndex":6}
              ],
              "entityRelations": [
                {"sourceEntityIndex":0,"targetEntityIndex":1,"type":"DERIVES","description":"定义推导出一般式表达式"},
                {"sourceEntityIndex":1,"targetEntityIndex":6,"type":"DERIVES","description":"配方法"},
                {"sourceEntityIndex":3,"targetEntityIndex":4,"type":"CONTAINS","description":"包含"},
                {"sourceEntityIndex":5,"targetEntityIndex":6,"type":"REFERENCES","description":"引用"}
              ],
              "prerequisites": [
                {"sourceKnowledgePointIndex":0,"targetKnowledgePointIndex":1,"strength":0.9,"description":"d"}
              ],
              "categoryRelations": [
                {"childCategoryIndex":1,"parentCategoryIndex":0},
                {"childCategoryIndex":2,"parentCategoryIndex":1},
                {"childCategoryIndex":3,"parentCategoryIndex":2}
              ]
            }""";

    private static final String SAMPLE_JSON_EXT = """
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
                {"name":"tn1"},
                {"name":"tn2"}
              ]
            }""";
}
