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
 * 图谱构建集成测试 — 直连 podman Neo4j/MySQL/DeepSeek。
 *
 * @author Jay
 * @date 2026/06/20
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("图谱构建集成测试")
class ConstructionControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private static final Long VALID_DOC_ID = 5L;
    private static final Long NONEXISTENT_DOC_ID = 99999L;

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.base-url", () -> "https://api.deepseek.com");
        registry.add("spring.ai.openai.chat.options.model", () -> "deepseek-v4-flash");
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/graph/construction";
    }

    @Test
    @Order(1)
    @DisplayName("AC-2: 旧 URL 返回 404")
    void testOldUrl_Returns404() {
        ResponseEntity<String> oldExtract = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/graph/extract/" + VALID_DOC_ID,
                org.springframework.http.HttpMethod.POST, null,
                new ParameterizedTypeReference<String>() {});
        assertEquals(HttpStatus.NOT_FOUND, oldExtract.getStatusCode(), "旧 extract URL 应返回 404");

        ResponseEntity<String> oldDoc = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/graph/document/" + VALID_DOC_ID,
                org.springframework.http.HttpMethod.GET, null,
                new ParameterizedTypeReference<String>() {});
        assertEquals(HttpStatus.NOT_FOUND, oldDoc.getStatusCode(), "旧 document URL 应返回 404");
    }

    @Test
    @Order(2)
    @DisplayName("AC-3: 新 URL 抽取成功 → LLM 真实调用 → Neo4j 写入")
    void testExtractGraph_Success() {
        var rt = this.restTemplate.getRestTemplate();
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(120000);
        rt.setRequestFactory(factory);

        ResponseEntity<String> response = rt.exchange(
                baseUrl() + "/extract/" + VALID_DOC_ID,
                org.springframework.http.HttpMethod.POST, null,
                new ParameterizedTypeReference<String>() {});

        System.out.println("Status: " + response.getStatusCode());
        if (response.getBody() != null) {
            System.out.println("Body (first 500): " + response.getBody().substring(0, Math.min(500, response.getBody().length())));
        }

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"edgeCount\""), "响应应包含 edgeCount");
    }

    @Test
    @Order(3)
    @DisplayName("AC-3: 查询文档子图 → 返回节点和边")
    void testGetSubgraph_Success() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/document/" + VALID_DOC_ID,
                org.springframework.http.HttpMethod.GET, null,
                new ParameterizedTypeReference<String>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"nodes\""), "应包含 nodes 数组");
        assertTrue(response.getBody().contains("\"edges\""), "应包含 edges 数组");
        System.out.println("子图查询通过: body 长度=" + response.getBody().length());
    }

    @Test
    @Order(4)
    @DisplayName("重复抽取为全量覆盖")
    void testExtractGraph_ReExtract_Overwrites() {
        ResponseEntity<String> reExtract = restTemplate.exchange(
                baseUrl() + "/extract/" + VALID_DOC_ID,
                org.springframework.http.HttpMethod.POST, null,
                new ParameterizedTypeReference<String>() {});
        assertEquals(HttpStatus.OK, reExtract.getStatusCode());

        ResponseEntity<String> subgraph = restTemplate.exchange(
                baseUrl() + "/document/" + VALID_DOC_ID,
                org.springframework.http.HttpMethod.GET, null,
                new ParameterizedTypeReference<String>() {});
        assertEquals(HttpStatus.OK, subgraph.getStatusCode());
        assertTrue(subgraph.getBody().contains("\"nodes\""));
    }

    @Test
    @Order(5)
    @DisplayName("不存在文档返回 404")
    void testExtractGraph_InvalidDocument_Rejected() {
        ResponseEntity<String> notFound = restTemplate.exchange(
                baseUrl() + "/extract/" + NONEXISTENT_DOC_ID,
                org.springframework.http.HttpMethod.POST, null,
                new ParameterizedTypeReference<String>() {});
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());
    }
}