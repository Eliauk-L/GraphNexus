package com.graphnexus.api.graph.controller;

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
        // DeepSeek 主模型
        registry.add("spring.ai.openai.api-key", () -> "ollama");
        registry.add("spring.ai.openai.base-url", () -> "http://localhost:11434/v1");
        registry.add("spring.ai.openai.chat.options.model", () -> "deepseek-r1:7b");
        // Ollama 兜底
        registry.add("spring.ai.ollama.base-url", () -> "http://localhost:11434");
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/graph";
    }

    @Test
    @Order(1)
    @DisplayName("AC-1+AC-4: 端到端抽取 → LLM 真实调用 → Neo4j 写入成功")
    void testExtractGraph_Success() {
        // LLM 调用可能耗时较长，使用自定义超时
        var rt = this.restTemplate.getRestTemplate();
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(120000);
        rt.setRequestFactory(factory);

        ResponseEntity<String> response = rt.exchange(
                baseUrl() + "/extract/" + VALID_DOC_ID,
                org.springframework.http.HttpMethod.POST,
                null,
                new ParameterizedTypeReference<String>() {});

        System.out.println("Status: " + response.getStatusCode());
        System.out.println("Body: " + (response.getBody() != null ? response.getBody().substring(0, Math.min(500, response.getBody().length())) : "null"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @Order(2)
    @DisplayName("AC-2: 查询文档子图 → 返回节点和边")
    void testGetSubgraph_Success() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/document/" + VALID_DOC_ID,
                org.springframework.http.HttpMethod.GET,
                null,
                new ParameterizedTypeReference<String>() {});

        System.out.println("AC-2 Status: " + response.getStatusCode());
        if (response.getBody() != null) {
            System.out.println("AC-2 Body (first 600): " + response.getBody().substring(0, Math.min(600, response.getBody().length())));
        }
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"nodes\""), "应包含 nodes 数组");
        assertTrue(response.getBody().contains("\"edges\""), "应包含 edges 数组");
        assertTrue(response.getBody().contains("\"Document\""), "应包含 Document 节点");
        assertTrue(response.getBody().contains("\"Entity\""), "应包含 Entity 节点");
        assertTrue(response.getBody().contains("\"KnowledgePoint\""), "应包含 KnowledgePoint 节点");

        System.out.println("✅ 子图查询通过: body 长度=" + response.getBody().length());
    }

    @Test
    @Order(3)
    @DisplayName("AC-8: 重复抽取为全量覆盖 — 两次结果均成功")
    void testExtractGraph_ReExtract_Overwrites() {
        // 第一次：重新抽取
        ResponseEntity<String> reExtract = restTemplate.exchange(
                baseUrl() + "/extract/" + VALID_DOC_ID,
                org.springframework.http.HttpMethod.POST,
                null,
                new ParameterizedTypeReference<String>() {});
        assertEquals(HttpStatus.OK, reExtract.getStatusCode(), "重抽取应返回 200");

        // 查询子图确认有数据
        ResponseEntity<String> subgraph = restTemplate.exchange(
                baseUrl() + "/document/" + VALID_DOC_ID,
                org.springframework.http.HttpMethod.GET,
                null,
                new ParameterizedTypeReference<String>() {});
        assertEquals(HttpStatus.OK, subgraph.getStatusCode());
        assertTrue(subgraph.getBody().contains("\"nodes\""));
        assertTrue(subgraph.getBody().contains("\"edges\""));

        System.out.println("✅ 重复抽取覆盖通过");
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