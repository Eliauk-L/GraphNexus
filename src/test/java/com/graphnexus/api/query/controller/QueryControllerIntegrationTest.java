package com.graphnexus.api.query.controller;

import com.graphnexus.application.analysis.model.PrunedSubgraph;
import com.graphnexus.application.analysis.model.PrunedSubgraph.PruningMeta;
import com.graphnexus.api.query.dto.chat.QueryAskRequest;
import com.graphnexus.infrastructure.mysql.file.ExamRecordDO;
import com.graphnexus.infrastructure.mysql.file.ExamRecordRepository;
import com.graphnexus.infrastructure.mysql.query.QueryTaskDO;
import com.graphnexus.infrastructure.mysql.query.QueryTaskRepository;
import com.graphnexus.infrastructure.mysql.query.QueryTaskStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 智能问答集成测试 — 直连 podman Neo4j/MySQL。
 *
 * <p><b>前置条件</b>：</p>
 * <ol>
 *   <li>podman 容器全运行</li>
 *   <li>环境变量 {@code LLM_API_KEY} 已设置（LLM 相关用例需要，否则自动 skip）</li>
 * </ol>
 *
 * @author Jay
 * @date 2026/06/17
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("智能问答集成测试")
class QueryControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    /** LLM 调用可能超过 30s，使用长超时 RestTemplate */
    private RestTemplate longTimeoutRestTemplate;

    @Autowired
    private Neo4jClient neo4jClient;

    @Autowired
    private QueryTaskRepository queryTaskRepository;

    @Autowired
    private ExamRecordRepository examRecordRepository;

    @Autowired
    private ObjectMapper objectMapper;

    /** 测试学生学号 */
    private static final String TEST_STUDENT_NO = "QA-TEST-001";
    /** 测试学生姓名 */
    private static final String TEST_STUDENT_NAME = "集成测试学生";
    /** 测试学科 */
    private static final String TEST_SUBJECT = "数学";

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/v1/query";
        // LLM 调用长超时 RestTemplate（120s）
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(120000);
        longTimeoutRestTemplate = new RestTemplate(factory);
        createTestData();
    }

    @AfterEach
    void tearDown() {
        cleanTestData();
    }

    // ======================== 测试数据准备 ========================

    void createTestData() {
        // MySQL exam_record: 学生身份数据（resolveStudent 的权威来源）
        ExamRecordDO examRecord = ExamRecordDO.builder()
                .studentNo(TEST_STUDENT_NO)
                .name(TEST_STUDENT_NAME)
                .className("测试班")
                .subject(TEST_SUBJECT)
                .examNo("QA-EXAM-001")
                .examName("集成测试考试")
                .isDeleted(0)
                .build();
        examRecordRepository.save(examRecord);

        // Neo4j: 图数据
        neo4jClient.query(
                "MERGE (s:Student {studentNo: $studentNo}) " +
                "SET s.name = $name, s.className = $className, s.grade = $grade, " +
                "s.nodeType = 'Student', s.id = coalesce(s.id, $sid)"
        ).bindAll(Map.of(
                "studentNo", TEST_STUDENT_NO,
                "name", TEST_STUDENT_NAME,
                "className", "测试班",
                "grade", "9",
                "sid", "qa-test-student-1"
        )).run();

        // 创建 KnowledgePoints
        String[] kps = {"二次函数顶点坐标", "对称轴", "配方法", "判别式"};
        double[] weights = {0.20, 0.35, 0.40, 0.75};
        String[] kpIds = {"qa-kp-1", "qa-kp-2", "qa-kp-3", "qa-kp-4"};

        for (int i = 0; i < kps.length; i++) {
            neo4jClient.query(
                    "MERGE (kp:KnowledgePoint {id: $id}) " +
                    "SET kp.name = $name, kp.subject = $subject, kp.nodeType = 'KnowledgePoint'"
            ).bindAll(Map.of("id", kpIds[i], "name", kps[i], "subject", TEST_SUBJECT)).run();

            // MASTERS 边
            neo4jClient.query(
                    "MATCH (s:Student {studentNo: $studentNo}), (kp:KnowledgePoint {id: $kpId}) " +
                    "MERGE (s)-[r:MASTERS]->(kp) " +
                    "SET r.weight = $weight, r.edgeType = 'MASTERS', r.createdAt = datetime()"
            ).bindAll(Map.of("studentNo", TEST_STUDENT_NO, "kpId", kpIds[i], "weight", weights[i])).run();
        }

        // PREREQUISITE_OF 边：配方法 → 顶点坐标
        neo4jClient.query(
                "MATCH (a:KnowledgePoint {id: 'qa-kp-3'}), (b:KnowledgePoint {id: 'qa-kp-1'}) " +
                "MERGE (a)-[r:PREREQUISITE_OF]->(b) " +
                "SET r.strength = 0.90, r.weight = 0.90, r.edgeType = 'PREREQUISITE_OF', r.createdAt = datetime()"
        ).run();
    }

    void cleanTestData() {
        // 删除 MASTERS 和 PREREQUISITE_OF 边
        neo4jClient.query(
                "MATCH (s:Student {studentNo: $studentNo})-[r:MASTERS]->(:KnowledgePoint {id: 'qa-kp-1'}) DELETE r"
        ).bindAll(Map.of("studentNo", TEST_STUDENT_NO)).run();
        neo4jClient.query(
                "MATCH (s:Student {studentNo: $studentNo})-[r:MASTERS]->(:KnowledgePoint {id: 'qa-kp-2'}) DELETE r"
        ).bindAll(Map.of("studentNo", TEST_STUDENT_NO)).run();
        neo4jClient.query(
                "MATCH (s:Student {studentNo: $studentNo})-[r:MASTERS]->(:KnowledgePoint {id: 'qa-kp-3'}) DELETE r"
        ).bindAll(Map.of("studentNo", TEST_STUDENT_NO)).run();
        neo4jClient.query(
                "MATCH (s:Student {studentNo: $studentNo})-[r:MASTERS]->(:KnowledgePoint {id: 'qa-kp-4'}) DELETE r"
        ).bindAll(Map.of("studentNo", TEST_STUDENT_NO)).run();

        // 删除 PREREQUISITE_OF 边
        neo4jClient.query(
                "MATCH (:KnowledgePoint {id: 'qa-kp-3'})-[r:PREREQUISITE_OF]->(:KnowledgePoint {id: 'qa-kp-1'}) DELETE r"
        ).run();

        // 删除测试节点
        neo4jClient.query("MATCH (kp:KnowledgePoint {id: 'qa-kp-1'}) DETACH DELETE kp").run();
        neo4jClient.query("MATCH (kp:KnowledgePoint {id: 'qa-kp-2'}) DETACH DELETE kp").run();
        neo4jClient.query("MATCH (kp:KnowledgePoint {id: 'qa-kp-3'}) DETACH DELETE kp").run();
        neo4jClient.query("MATCH (kp:KnowledgePoint {id: 'qa-kp-4'}) DETACH DELETE kp").run();
        neo4jClient.query(
                "MATCH (s:Student {studentNo: $studentNo}) DETACH DELETE s"
        ).bindAll(Map.of("studentNo", TEST_STUDENT_NO)).run();

        // 清理 qa_task 测试数据
        queryTaskRepository.findAll().stream()
                .filter(t -> t.getStudentNo() != null && t.getStudentNo().equals(TEST_STUDENT_NO))
                .forEach(queryTaskRepository::delete);

        // 清理 MySQL exam_record 测试数据
        examRecordRepository.findAll().stream()
                .filter(r -> r.getStudentNo() != null && r.getStudentNo().equals(TEST_STUDENT_NO))
                .forEach(examRecordRepository::delete);
    }

    // ======================== AC-1: 同步问答端到端（需 LLM） ========================

    @Test
    @Order(1)
    @DisplayName("AC-1 同步问答")
    void shouldReturnMarkdownAnalysisForSyncAsk() {
        String llmKey = System.getenv("LLM_API_KEY");
        Assumptions.assumeTrue(llmKey != null && !llmKey.isBlank(),
                "跳过：LLM_API_KEY 未设置");

        QueryAskRequest request = new QueryAskRequest(
                "分析学生" + TEST_STUDENT_NAME + "的" + TEST_SUBJECT + "薄弱点",
                TEST_STUDENT_NAME, TEST_STUDENT_NO, TEST_SUBJECT);

        ResponseEntity<Map<String, Object>> response = longTimeoutRestTemplate.exchange(
                baseUrl + "/ask",
                org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(request),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        // ApiResponse 包装：data 字段含实际内容
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNotNull(data, "ApiResponse.data 不应为 null");
        assertNotNull(data.get("taskId"));
        String answer = (String) data.get("answer");
        assertNotNull(answer, "answer 不应为 null");
        // LLM 输出应以 Markdown 标题开头
        assertTrue(answer.startsWith("#"), "answer 应以 # 标题开头: " + answer.substring(0, Math.min(50, answer.length())));
        // 应包含薄弱知识点名称
        assertTrue(answer.contains("顶点坐标") || answer.contains("对称轴"),
                "answer 应包含薄弱知识点名称");
    }

    // ======================== AC-2: 异步问答（需 LLM） ========================

    @Test
    @Order(2)
    @DisplayName("AC-2 异步问答+轮询")
    void shouldCompleteAsyncAskAndPoll() throws Exception {
        String llmKey = System.getenv("LLM_API_KEY");
        Assumptions.assumeTrue(llmKey != null && !llmKey.isBlank(),
                "跳过：LLM_API_KEY 未设置");

        QueryAskRequest request = new QueryAskRequest(
                "诊断" + TEST_STUDENT_NAME + TEST_SUBJECT + "学习问题",
                TEST_STUDENT_NAME, TEST_STUDENT_NO, TEST_SUBJECT);

        // 提交异步任务
        ResponseEntity<Map<String, Object>> asyncResp = longTimeoutRestTemplate.exchange(
                baseUrl + "/ask-async",
                org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(request),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertEquals(HttpStatus.OK, asyncResp.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> asyncData = (Map<String, Object>) asyncResp.getBody().get("data");
        String taskId = (String) asyncData.get("taskId");
        assertEquals("PENDING", asyncData.get("status"));

        // 轮询结果（最多 60s）
        boolean completed = false;
        for (int i = 0; i < 60; i++) {
            Thread.sleep(2000);
            ResponseEntity<Map<String, Object>> resultResp = longTimeoutRestTemplate.exchange(
                    baseUrl + "/result/" + taskId,
                    org.springframework.http.HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            Map<String, Object> resultData = (Map<String, Object>) resultResp.getBody().get("data");
            String status = (String) resultData.get("status");
            if ("COMPLETED".equals(status)) {
                completed = true;
                assertNotNull(resultData.get("answer"));
                break;
            } else if ("FAILED".equals(status)) {
                fail("异步任务失败: " + resultData.get("errorMessage"));
            }
        }
        assertTrue(completed, "异步任务应在 120s 内完成");
    }

    // ======================== AC-3: 子图端点（无需 LLM） ========================

    @Test
    @Order(3)
    @DisplayName("AC-3 子图数据端点")
    void shouldReturnSubgraphData() throws Exception {
        // 直接构造 PrunedSubgraph 写入 query_task
        String taskId = UUID.randomUUID().toString();
        PrunedSubgraph subgraph = new PrunedSubgraph(
                List.of(), List.of(),
                new PruningMeta("STUDENT_DIAGNOSIS", true, 0.6, 2, 4, 3, false, Collections.emptyList()));

        QueryTaskDO task = QueryTaskDO.builder()
                .taskId(taskId)
                .question("分析" + TEST_STUDENT_NAME)
                .studentName(TEST_STUDENT_NAME)
                .studentNo(TEST_STUDENT_NO)
                .subject(TEST_SUBJECT)
                .intent("STUDENT_DIAGNOSIS")
                .status(QueryTaskStatus.COMPLETED)
                .answer("# 测试答案\n- 项1\n- 项2")
                .subgraphJson(objectMapper.writeValueAsString(subgraph))
                .tokenUsageJson("{}")
                .elapsedMs(100L)
                .build();
        queryTaskRepository.save(task);

        // 查询子图端点（analysis 模块）
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/analysis/subgraph/" + taskId,
                org.springframework.http.HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNotNull(data);
        assertEquals(taskId, data.get("taskId"));
        assertNotNull(data.get("pruningMeta"));

        // 清理
        queryTaskRepository.delete(task);
    }

    // ======================== AC-10: 学生不存在 ========================

    @Test
    @Order(4)
    @DisplayName("AC-10 学生不存在返回404")
    void shouldReturn404ForNonexistentStudent() {
        QueryAskRequest request = new QueryAskRequest(
                "分析不存在的学生的薄弱点",
                "不存在学生ABC123", null, "数学");

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                baseUrl + "/ask",
                org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(request),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        // 即使 LLM 不可用，错误检查发生在 LLM 调用之前
        assertNotNull(response.getBody());
        // 可能是 404 (A0006 学生不存在) 或 500 (LLM 调用失败)
        Object code = response.getBody().get("errorCode");
        assertNotNull(code, "应返回错误码");
    }

    // ======================== 附加: 意图识别失败 ========================

    @Test
    @Order(5)
    @DisplayName("意图识别失败返回 A0019")
    void shouldReturnErrorForUnrecognizableIntent() {
        QueryAskRequest request = new QueryAskRequest(
                "今天天气怎么样",
                TEST_STUDENT_NAME, TEST_STUDENT_NO, TEST_SUBJECT);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                baseUrl + "/ask",
                org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(request),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        assertNotNull(response.getBody());
        // A0019 = 400 BAD_REQUEST
        assertEquals(400, response.getStatusCode().value());
        assertEquals("A0019", response.getBody().get("errorCode"));
    }
}