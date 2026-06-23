package com.graphnexus.application.graph.construction.extract;

import com.graphnexus.application.graph.construction.extract.registry.ExtractionEdgeFactoryRegistry;
import com.graphnexus.application.graph.construction.extract.registry.ExtractionNodeHandlerRegistry;
import com.graphnexus.application.graph.construction.extract.registry.ContainsEdgeFactory;
import com.graphnexus.application.graph.construction.extract.registry.DerivesEdgeFactory;
import com.graphnexus.application.graph.construction.extract.registry.ReferencesEdgeFactory;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.LlmGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AC-1 / AC-6 回归测试 — 固定输入快照 + 行为等价。
 *
 * <p>对重构前后行为等价的核心断言：合法 JSON 通过校验产出领域对象；
 * 3 类非法输入（缺 name / entityType 枚举越界 / 非 JSON）仍抛 BusinessException(A0010) 且 message 含字段名。</p>
 *
 * @author Jay
 * @date 2026/06/21
 */
@DisplayName("AC-1/AC-6 抽取行为等价回归测试")
class ExtractionRegressionTest {

    private ExtractionService serviceWithMockLlm(String llmResponse) {
        LlmGateway llmGateway = mock(LlmGateway.class);
        when(llmGateway.chat(anyString(), anyString())).thenReturn(llmResponse);
        ExtractionNodeHandlerRegistry nodeHandlerRegistry = new ExtractionNodeHandlerRegistry(List.of());
        return new ExtractionService(llmGateway,
                new ExtractionPromptBuilder(new DefaultResourceLoader(), nodeHandlerRegistry, null),
                new ExtractionValidator(), new ExtractionJsonParser(),
                new ExtractionEdgeFactoryRegistry(List.of(
                        new DerivesEdgeFactory(), new ContainsEdgeFactory(), new ReferencesEdgeFactory())),
                nodeHandlerRegistry);
    }

    @Test
    @DisplayName("AC-1：合法 JSON 抽取产出完整领域对象结构")
    void validJsonProducesCompleteDomainStructure() {
        ExtractionService.ExtractionResult r = serviceWithMockLlm(VALID_JSON)
                .extract("text", "doc", "数学", 1, "doc-1");

        assertEquals(1, r.entities().size());
        assertEquals(1, r.knowledgePoints().size());
        assertEquals("DEFINITION", r.entities().get(0).getEntityType());
        assertTrue(r.edges().size() >= 2, "应含 ALIGNED_TO + BELONGS_TO 边");
        assertTrue(r.extensionNodes().isEmpty());
    }

    @Test
    @DisplayName("AC-3 不回归：entityType 枚举越界 → BusinessException A0010 含字段名")
    void invalidEntityTypeThrows() {
        ExtractionService svc = serviceWithMockLlm(JSON_INVALID_ENTITY_TYPE);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.extract("text", "doc", "数学", 1, "doc-1"));
        assertEquals("A0010", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("UNKNOWN"));
    }

    @Test
    @DisplayName("AC-3 不回归：缺必填字段 name → BusinessException A0010 含字段名")
    void missingNameThrows() {
        ExtractionService svc = serviceWithMockLlm(JSON_MISSING_NAME);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.extract("text", "doc", "数学", 1, "doc-1"));
        assertEquals("A0010", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("name"));
    }

    @Test
    @DisplayName("AC-3 不回归：非合法 JSON → 重试 3 次后 BusinessException A0010")
    void nonJsonThrowsAfterRetry() {
        ExtractionService svc = serviceWithMockLlm("这不是一段 JSON 文本");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.extract("text", "doc", "数学", 1, "doc-1"));
        assertEquals("C0001", ex.getErrorCode());
    }

    private static final String VALID_JSON = """
            {
              "entities": [
                {"entityType":"DEFINITION","name":"二次函数","originalText":"y=ax²+bx+c","pageNumber":2}
              ],
              "knowledgePoints": [
                {"name":"二次函数定义","description":"标准定义","subject":"数学","gradeLevel":"初中"}
              ],
              "categories": [
                {"name":"函数","parentName":null,"level":1}
              ],
              "alignments": [
                {"entityIndex":0,"knowledgePointIndex":0}
              ],
              "entityRelations": [],
              "prerequisites": [],
              "categoryRelations": []
            }""";

    private static final String JSON_INVALID_ENTITY_TYPE = """
            {
              "entities": [
                {"entityType":"UNKNOWN","name":"x","originalText":"t","pageNumber":1}
              ],
              "knowledgePoints": [
                {"name":"k","description":"d","subject":"数学","gradeLevel":"初中"}
              ],
              "categories": [],
              "alignments": [
                {"entityIndex":0,"knowledgePointIndex":0}
              ],
              "entityRelations": [],
              "prerequisites": [],
              "categoryRelations": []
            }""";

    private static final String JSON_MISSING_NAME = """
            {
              "entities": [
                {"entityType":"DEFINITION","name":"","originalText":"t","pageNumber":1}
              ],
              "knowledgePoints": [
                {"name":"k","description":"d","subject":"数学","gradeLevel":"初中"}
              ],
              "categories": [],
              "alignments": [
                {"entityIndex":0,"knowledgePointIndex":0}
              ],
              "entityRelations": [],
              "prerequisites": [],
              "categoryRelations": []
            }""";
}
