package com.graphnexus.api.graph.controller;

import com.graphnexus.common.ApiResponse;
import com.graphnexus.api.graph.dto.FusionExecuteVO;
import com.graphnexus.api.graph.dto.FusionStatusVO;
import com.graphnexus.api.graph.dto.FusionRollbackVO;
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
    @DisplayName("AC-1: 手动全量融合 — POST /execute 返回成功")
    void testManualFullFusion() {
        ResponseEntity<ApiResponse<FusionExecuteVO>> response = restTemplate.exchange(
                baseUrl() + "/execute",
                org.springframework.http.HttpMethod.POST,
                null,
                new ParameterizedTypeReference<ApiResponse<FusionExecuteVO>>() {});

        System.out.println("Execute Status: " + response.getStatusCode());
        ApiResponse<FusionExecuteVO> body = response.getBody();
        if (body != null && body.data() != null) {
            System.out.println("Execute Body: fusionLogId=" + body.data().fusionLogId() +
                    ", mergedKpGroups=" + body.data().mergedKpGroupCount());
            lastFusionLogId = body.data().fusionLogId();
        }

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(body);
        assertEquals(200, body.code());
    }

    @Test
    @Order(2)
    @DisplayName("AC-7: 融合状态查询 — GET /status 返回最近融合记录")
    void testFusionStatus() {
        // 先触发一次融合确保有记录
        restTemplate.exchange(
                baseUrl() + "/execute",
                org.springframework.http.HttpMethod.POST,
                null,
                new ParameterizedTypeReference<ApiResponse<FusionExecuteVO>>() {});

        ResponseEntity<ApiResponse<FusionStatusVO>> response = restTemplate.exchange(
                baseUrl() + "/status",
                org.springframework.http.HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<FusionStatusVO>>() {});

        System.out.println("Status: " + response.getStatusCode());
        ApiResponse<FusionStatusVO> body = response.getBody();
        if (body != null && body.data() != null) {
            System.out.println("Status Result: triggerType=" + body.data().triggerType() +
                    ", status=" + body.data().status());
        }

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
    @DisplayName("AC-8: 融合后回滚 — POST /rollback/{id} 返回成功")
    void testFusionAndRollback() {
        // 1. 先执行融合
        ResponseEntity<ApiResponse<FusionExecuteVO>> execResponse = restTemplate.exchange(
                baseUrl() + "/execute",
                org.springframework.http.HttpMethod.POST,
                null,
                new ParameterizedTypeReference<ApiResponse<FusionExecuteVO>>() {});

        assertEquals(HttpStatus.OK, execResponse.getStatusCode());
        Long fusionLogId = execResponse.getBody() != null && execResponse.getBody().data() != null
                ? execResponse.getBody().data().fusionLogId() : null;
        assertNotNull(fusionLogId, "融合应返回有效的 fusionLogId");
        System.out.println("Fusion executed: fusionLogId=" + fusionLogId);

        // 2. 回滚
        ResponseEntity<ApiResponse<FusionRollbackVO>> rollbackResponse = restTemplate.exchange(
                baseUrl() + "/rollback/" + fusionLogId,
                org.springframework.http.HttpMethod.POST,
                null,
                new ParameterizedTypeReference<ApiResponse<FusionRollbackVO>>() {});

        System.out.println("Rollback Status: " + rollbackResponse.getStatusCode());
        assertEquals(HttpStatus.OK, rollbackResponse.getStatusCode());
        assertNotNull(rollbackResponse.getBody());
        assertEquals(200, rollbackResponse.getBody().code());
    }

    @Test
    @Order(5)
    @DisplayName("AC-9: 回滚幂等 — 已回滚日志再次回滚无副作用")
    void testRollbackIdempotent() {
        // 执行融合
        ResponseEntity<ApiResponse<FusionExecuteVO>> execResponse = restTemplate.exchange(
                baseUrl() + "/execute",
                org.springframework.http.HttpMethod.POST,
                null,
                new ParameterizedTypeReference<ApiResponse<FusionExecuteVO>>() {});
        Long fusionLogId = execResponse.getBody().data().fusionLogId();

        // 第一次回滚
        restTemplate.exchange(
                baseUrl() + "/rollback/" + fusionLogId,
                org.springframework.http.HttpMethod.POST,
                null,
                new ParameterizedTypeReference<ApiResponse<FusionRollbackVO>>() {});

        // 第二次回滚 — 幂等成功
        ResponseEntity<ApiResponse<FusionRollbackVO>> secondResponse = restTemplate.exchange(
                baseUrl() + "/rollback/" + fusionLogId,
                org.springframework.http.HttpMethod.POST,
                null,
                new ParameterizedTypeReference<ApiResponse<FusionRollbackVO>>() {});

        System.out.println("Second Rollback Code: " +
                (secondResponse.getBody() != null ? secondResponse.getBody().code() : "null"));
        assertEquals(HttpStatus.OK, secondResponse.getStatusCode());
        assertEquals(200, secondResponse.getBody().code());
    }
}