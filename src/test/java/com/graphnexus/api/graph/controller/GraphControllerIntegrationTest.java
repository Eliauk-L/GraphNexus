package com.graphnexus.api.graph.controller;

import com.graphnexus.api.graph.dto.ExtractionResultVO;
import com.graphnexus.api.graph.dto.GraphSubgraphVO;
import com.graphnexus.common.ApiResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 知识图谱抽取集成测试 — 直连 podman 中的 Neo4j/MySQL/DeepSeek API。
 *
 * <p><b>前置条件（全部满足才能运行）</b>：</p>
 * <ol>
 *   <li>podman 容器全运行：Neo4j / MySQL / MinIO / Redis / RabbitMQ</li>
 *   <li>环境变量 {@code DEEPSEEK_API_KEY} 已设置</li>
 *   <li>MySQL {@code graphnexus} 库中存在 status=COMPLETED 且 text_content 非空的文档</li>
 *   <li>应用可正常启动（{@code mvn spring-boot:run} 无报错）</li>
 * </ol>
 *
 * @author Jay
 * @date 2026/06/13
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("知识图谱抽取集成测试")
class GraphControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    /** 指向一条已 COMPLETED 的文档 ID（通过 podman exec mysql 插入的测试数据） */
    private static final Long VALID_DOC_ID = 3L;

    /** 指向一条不存在的文档 ID */
    private static final Long NONEXISTENT_DOC_ID = 99999L;

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey != null && !apiKey.isBlank()) {
            registry.add("spring.ai.openai.api-key", () -> apiKey);
            registry.add("spring.ai.openai.base-url", () -> "https://token.cvte.com");
            registry.add("spring.ai.openai.chat.options.model", () -> "deepseek-v4-flash");
        } else {
            System.err.println("WARNING: DEEPSEEK_API_KEY 环境变量未设置，集成测试可能因 ChatModel bean 缺失而失败");
        }
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/graph";
    }

    @Test
    @Order(1)
    @DisplayName("AC-1+AC-4: 端到端抽取 → LLM 真实调用 → Neo4j 写入成功")
    void testExtractGraph_Success() {
        ResponseEntity<ApiResponse<ExtractionResultVO>> response = restTemplate.exchange(
                baseUrl() + "/extract/" + VALID_DOC_ID,
                org.springframework.http.HttpMethod.POST,
                null,
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ApiResponse<ExtractionResultVO> body = response.getBody();
        assertNotNull(body);
        assertEquals(200, body.code());
        ExtractionResultVO result = body.data();
        assertNotNull(result, "抽取结果不应为 null");
        assertTrue(result.getEntityCount() > 0, "实体数量应 > 0，实际=" + result.getEntityCount());
        assertTrue(result.getKnowledgePointCount() > 0, "知识点数量应 > 0");
        assertTrue(result.getEdgeCount() > 0, "边数量应 > 0");

        System.out.printf("✅ 抽取完成: entities=%d, kp=%d, categories=%d, edges=%d%n",
                result.getEntityCount(), result.getKnowledgePointCount(),
                result.getCategoryCount(), result.getEdgeCount());
    }

    @Test
    @Order(2)
    @DisplayName("AC-2: 查询文档子图 → 返回节点和边")
    void testGetSubgraph_Success() {
        ResponseEntity<ApiResponse<GraphSubgraphVO>> response = restTemplate.exchange(
                baseUrl() + "/document/" + VALID_DOC_ID,
                org.springframework.http.HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ApiResponse<GraphSubgraphVO> body = response.getBody();
        assertNotNull(body);
        GraphSubgraphVO subgraph = body.data();
        assertNotNull(subgraph);
        assertNotNull(subgraph.getNodes(), "nodes 不应为 null");
        assertNotNull(subgraph.getEdges(), "edges 不应为 null");
        assertFalse(subgraph.getNodes().isEmpty(), "nodes 不应为空");
        assertFalse(subgraph.getEdges().isEmpty(), "edges 不应为空");

        // 验证节点包含不同 label
        boolean hasDocument = subgraph.getNodes().stream()
                .anyMatch(n -> "Document".equals(n.getNodeType()));
        boolean hasEntity = subgraph.getNodes().stream()
                .anyMatch(n -> "Entity".equals(n.getNodeType()));
        boolean hasKnowledgePoint = subgraph.getNodes().stream()
                .anyMatch(n -> "KnowledgePoint".equals(n.getNodeType()));

        assertTrue(hasDocument, "应包含 Document 节点");
        assertTrue(hasEntity, "应包含 Entity 节点");
        assertTrue(hasKnowledgePoint, "应包含 KnowledgePoint 节点");

        System.out.printf("✅ 子图查询: nodes=%d, edges=%d%n",
                subgraph.getNodes().size(), subgraph.getEdges().size());
    }

    @Test
    @Order(3)
    @DisplayName("AC-8: 重复抽取为全量覆盖 — 两次结果数量级一致")
    void testExtractGraph_ReExtract_Overwrites() {
        // 第一次查询当前子图
        ResponseEntity<ApiResponse<GraphSubgraphVO>> response1 = restTemplate.exchange(
                baseUrl() + "/document/" + VALID_DOC_ID,
                org.springframework.http.HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        int nodes1 = response1.getBody().data().getNodes().size();

        // 重新抽取
        restTemplate.exchange(
                baseUrl() + "/extract/" + VALID_DOC_ID,
                org.springframework.http.HttpMethod.POST,
                null,
                new ParameterizedTypeReference<ApiResponse<ExtractionResultVO>>() {});

        // 第二次查询
        ResponseEntity<ApiResponse<GraphSubgraphVO>> response2 = restTemplate.exchange(
                baseUrl() + "/document/" + VALID_DOC_ID,
                org.springframework.http.HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        int nodes2 = response2.getBody().data().getNodes().size();
        assertTrue(nodes2 > 0, "重抽取后节点数应 > 0");

        double diffRatio = Math.abs(nodes1 - nodes2) / (double) Math.max(nodes1, 1);
        assertTrue(diffRatio <= 0.2,
                String.format("两次抽取节点数差异应 ≤ 20%%，实际 nodes1=%d, nodes2=%d, diff=%.1f%%",
                        nodes1, nodes2, diffRatio * 100));

        System.out.printf("✅ 重复抽取覆盖: nodes1=%d, nodes2=%d, diff=%.1f%%%n",
                nodes1, nodes2, diffRatio * 100);
    }

    @Test
    @Order(4)
    @DisplayName("AC-7: 不存在文档拒绝 → HTTP 404")
    void testExtractGraph_InvalidDocument_Rejected() {
        ResponseEntity<String> notFound = restTemplate.exchange(
                baseUrl() + "/extract/" + NONEXISTENT_DOC_ID,
                org.springframework.http.HttpMethod.POST,
                null,
                new ParameterizedTypeReference<String>() {});

        System.out.println("=== DEBUG: " + notFound.getStatusCode() + " ===");
        System.out.println("Body: " + notFound.getBody());
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());
    }
}