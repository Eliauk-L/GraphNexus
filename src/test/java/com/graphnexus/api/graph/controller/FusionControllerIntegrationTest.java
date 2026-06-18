package com.graphnexus.api.graph.controller;

import com.graphnexus.common.ApiResult;
import com.graphnexus.api.graph.dto.fusion.FusionExecuteVO;
import com.graphnexus.api.graph.dto.fusion.FusionStatusVO;
import com.graphnexus.api.graph.dto.fusion.FusionRollbackVO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 宽图谱融合集成测试 — 直连 podman 中的 Neo4j + MySQL。
 *
 * <p><b>前置条件</b>：podman 容器全运行 (Neo4j/MySQL)，fusion_log 表已创建。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("宽图谱融合集成测试")
class FusionControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private Neo4jClient neo4jClient;

    private static final String SUBJECT = "数学";
    private static Long lastFusionLogId;

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/graph/fusion";
    }

    @BeforeEach
    void setUp() {
        neo4jClient.query(
                "MERGE (kp1:KnowledgePoint {id: 'test-kp-doc', name: '二次函数顶点坐标', subject: $subject}) " +
                "SET kp1.documentId = 'test-doc-1', kp1.fusionSource = 'DOCUMENT', kp1.nodeType = 'KnowledgePoint', " +
                "kp1.description = '顶点坐标公式测试', kp1.gradeLevel = '初中'"
        ).bindAll(Map.of("subject", SUBJECT)).run();

        neo4jClient.query(
                "MERGE (kp2:KnowledgePoint {id: 'test-kp-csv', name: '二次函数顶点坐标', subject: $subject}) " +
                "SET kp2.fusionSource = 'CSV_IMPORT', kp2.nodeType = 'KnowledgePoint'"
        ).bindAll(Map.of("subject", SUBJECT)).run();
    }

    @AfterEach
    void tearDown() {
        neo4jClient.query(
                "MATCH (kp:KnowledgePoint) WHERE kp.id STARTS WITH 'test-kp' DETACH DELETE kp"
        ).run();
        neo4jClient.query(
                "MATCH ()-[r:MASTERS]->(kp:KnowledgePoint) WHERE kp.id STARTS WITH 'test-kp' DELETE r"
        ).run();
    }

    @Test
    @Order(1)
    @DisplayName("AC-1: 手动全量融合 — POST /execute 返回 200")
    void testManualFullFusion() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/execute",
                org.springframework.http.HttpMethod.POST,
                null,
                new ParameterizedTypeReference<String>() {});

        System.out.println("Execute Status: " + response.getStatusCode());
        System.out.println("Execute Body: " + (response.getBody() != null
                ? response.getBody().substring(0, Math.min(300, response.getBody().length())) : "null"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @Order(2)
    @DisplayName("AC-7: 融合状态查询 — GET /status 返回 200")
    void testFusionStatus() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/status",
                org.springframework.http.HttpMethod.GET,
                null,
                new ParameterizedTypeReference<String>() {});

        System.out.println("Status: " + response.getStatusCode());
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @Order(3)
    @DisplayName("AC-10: 回滚不存在日志 — POST /rollback/99999 返回 4xx")
    void testRollbackNonexistent() {
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/rollback/99999",
                org.springframework.http.HttpMethod.POST,
                null,
                new ParameterizedTypeReference<String>() {});

        System.out.println("Rollback 99999 Status: " + response.getStatusCode());
        assertTrue(response.getStatusCode().is4xxClientError(),
                "Expected 4xx but got " + response.getStatusCode());
    }

    @Test
    @Order(4)
    @DisplayName("AC-8: 融合后回滚 — POST /rollback/{id} 返回 200")
    void testFusionAndRollback() {
        // 1. 先执行融合，获取 fusionLogId
        ResponseEntity<String> execResponse = restTemplate.exchange(
                baseUrl() + "/execute",
                org.springframework.http.HttpMethod.POST,
                null,
                new ParameterizedTypeReference<String>() {});
        assertEquals(HttpStatus.OK, execResponse.getStatusCode());

        String body = execResponse.getBody();
        assertNotNull(body);
        Long fusionLogId = extractFusionLogId(body);
        assertNotNull(fusionLogId, "融合应返回有效的 fusionLogId");
        System.out.println("Fusion executed: fusionLogId=" + fusionLogId);

        // 2. 回滚
        ResponseEntity<String> rollbackResponse = restTemplate.exchange(
                baseUrl() + "/rollback/" + fusionLogId,
                org.springframework.http.HttpMethod.POST,
                null,
                new ParameterizedTypeReference<String>() {});

        System.out.println("Rollback Status: " + rollbackResponse.getStatusCode());
        assertEquals(HttpStatus.OK, rollbackResponse.getStatusCode());
    }

    @Test
    @Order(5)
    @DisplayName("AC-9: 回滚幂等 — 二次回滚仍返回 200")
    void testRollbackIdempotent() {
        // 执行融合
        ResponseEntity<String> execResponse = restTemplate.exchange(
                baseUrl() + "/execute",
                org.springframework.http.HttpMethod.POST,
                null,
                new ParameterizedTypeReference<String>() {});
        Long fusionLogId = extractFusionLogId(execResponse.getBody());

        // 第一次回滚
        restTemplate.exchange(
                baseUrl() + "/rollback/" + fusionLogId,
                org.springframework.http.HttpMethod.POST,
                null,
                new ParameterizedTypeReference<String>() {});

        // 第二次回滚 — 幂等成功
        ResponseEntity<String> secondResponse = restTemplate.exchange(
                baseUrl() + "/rollback/" + fusionLogId,
                org.springframework.http.HttpMethod.POST,
                null,
                new ParameterizedTypeReference<String>() {});

        System.out.println("Second Rollback Status: " + secondResponse.getStatusCode());
        assertEquals(HttpStatus.OK, secondResponse.getStatusCode());
    }

    /** 从 JSON 响应中提取 fusionLogId */
    private Long extractFusionLogId(String json) {
        if (json == null) return null;
        try {
            // 匹配 "fusionLogId":数字
            int idx = json.indexOf("\"fusionLogId\"");
            if (idx < 0) return null;
            int colon = json.indexOf(":", idx);
            int end = json.indexOf(",", colon);
            if (end < 0) end = json.indexOf("}", colon);
            String num = json.substring(colon + 1, end).trim();
            return Long.parseLong(num);
        } catch (Exception e) {
            return null;
        }
    }
}