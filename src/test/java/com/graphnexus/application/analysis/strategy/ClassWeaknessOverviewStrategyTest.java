package com.graphnexus.application.analysis.strategy;

import com.graphnexus.application.analysis.model.PruningRequest;
import com.graphnexus.application.analysis.model.PrunedSubgraph;
import com.graphnexus.infrastructure.mysql.file.entity.ExamRecordDO;
import com.graphnexus.infrastructure.mysql.file.repository.ExamRecordRepository;
import com.graphnexus.infrastructure.neo4j.repository.QueryGraphRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * ClassWeaknessOverviewStrategy 单元测试 — 验证班级剪枝 5 步流程。
 */
@ExtendWith(MockitoExtension.class)
class ClassWeaknessOverviewStrategyTest {

    @Mock
    private QueryGraphRepository queryGraphRepository;

    @Mock
    private ExamRecordRepository examRecordRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ClassWeaknessOverviewStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new ClassWeaknessOverviewStrategy(queryGraphRepository, examRecordRepository, objectMapper);
    }

    // ======================== 班级不存在 ========================

    @Test
    void shouldReturnClassNotFoundWhenNoStudents() {
        when(examRecordRepository.findDistinctStudentsByClassName("不存在的班级"))
                .thenReturn(Collections.emptyList());

        PrunedSubgraph result = strategy.prune(new PruningRequest(
                "CLASS_WEAKNESS_OVERVIEW", "不存在的班级", "数学", Map.of()));

        assertEquals("CLASS_NOT_FOUND", result.meta().strategy());
        assertTrue(result.nodes().isEmpty());
    }

    // ======================== 正常路径：MASTERS 可用 ========================

    @Test
    void shouldAggregateKpsWhenMastersAvailable() {
        // 班级 3 个学生
        List<Object[]> classStudents = Arrays.asList(
                new Object[]{"S001", "张三", "初三(1)班"},
                new Object[]{"S002", "李四", "初三(1)班"},
                new Object[]{"S003", "王五", "初三(1)班"}
        );
        when(examRecordRepository.findDistinctStudentsByClassName("初三(1)班"))
                .thenReturn(classStudents);

        // MASTERS: 3 学生对 2 个 KP 的掌握度
        List<Map<String, Object>> mastersRows = List.of(
                createMastersRow("S001", "张三", "kp1", "配方法", 0.3),
                createMastersRow("S002", "李四", "kp1", "配方法", 0.5),
                createMastersRow("S003", "王五", "kp1", "配方法", 0.7),
                createMastersRow("S001", "张三", "kp2", "顶点坐标", 0.2),
                createMastersRow("S002", "李四", "kp2", "顶点坐标", 0.4),
                createMastersRow("S003", "王五", "kp2", "顶点坐标", 0.9)
        );
        when(queryGraphRepository.findMastersByStudentNos(
                eq(List.of("S001", "S002", "S003")), eq("数学")))
                .thenReturn(mastersRows);

        // 无前置依赖
        when(queryGraphRepository.findPrerequisitesUpstream(anyList(), eq(2)))
                .thenReturn(Collections.emptyList());

        PrunedSubgraph result = strategy.prune(new PruningRequest(
                "CLASS_WEAKNESS_OVERVIEW", "初三(1)班", "数学",
                Map.of("weakThreshold", 0.6, "maxHops", 2.0)));

        // 验证结果
        assertTrue(result.meta().mastersAvailable());
        assertTrue(result.meta().totalNodes() >= 3); // ClassInfo + 2 KP
        assertTrue(result.meta().totalEdges() >= 2); // 2 条聚合 MASTERS

        // 验证 ClassInfo 节点存在
        assertTrue(result.nodes().stream().anyMatch(n -> "ClassInfo".equals(n.nodeType())));

        // 验证聚合 MASTERS 边
        assertTrue(result.edges().stream().anyMatch(e -> "MASTERS".equals(e.edgeType())));
    }

    // ======================== 聚合正确性 ========================

    @Test
    void shouldAggregateCorrectly() {
        List<Object[]> classStudents = Arrays.asList(
                new Object[]{"S001", "张三", "初三(1)班"},
                new Object[]{"S002", "李四", "初三(1)班"},
                new Object[]{"S003", "王五", "初三(1)班"}
        );
        when(examRecordRepository.findDistinctStudentsByClassName("初三(1)班"))
                .thenReturn(classStudents);

        // 3 人对同一 KP 的 weight: 0.3, 0.5, 0.7 → avg=0.5, weakCount=3 (all < 0.6)
        List<Map<String, Object>> mastersRows = List.of(
                createMastersRow("S001", "张三", "kp1", "配方法", 0.3),
                createMastersRow("S002", "李四", "kp1", "配方法", 0.5),
                createMastersRow("S003", "王五", "kp1", "配方法", 0.7)
        );
        when(queryGraphRepository.findMastersByStudentNos(anyList(), eq("数学")))
                .thenReturn(mastersRows);
        when(queryGraphRepository.findPrerequisitesUpstream(anyList(), eq(2)))
                .thenReturn(Collections.emptyList());

        PrunedSubgraph result = strategy.prune(new PruningRequest(
                "CLASS_WEAKNESS_OVERVIEW", "初三(1)班", "数学",
                Map.of("weakThreshold", 0.6, "maxHops", 2.0)));

        // 验证 KP 节点的聚合属性
        var kpNode = result.nodes().stream()
                .filter(n -> "kp1".equals(n.id()))
                .findFirst().orElseThrow();
        assertEquals(0.5, ((Number) kpNode.properties().get("avgWeight")).doubleValue(), 0.001);
        assertEquals(3, ((Number) kpNode.properties().get("totalCount")).intValue());
    }

    // ======================== MASTERS 降级 ========================

    @Test
    void shouldDegradeWhenMastersUnavailable() {
        List<Object[]> classStudents = Collections.singletonList(
                new Object[]{"S001", "张三", "初三(1)班"}
        );
        when(examRecordRepository.findDistinctStudentsByClassName("初三(1)班"))
                .thenReturn(classStudents);

        // MASTERS 不可用
        when(queryGraphRepository.findMastersByStudentNos(anyList(), eq("数学")))
                .thenReturn(Collections.emptyList());

        // TESTED 降级路径
        when(queryGraphRepository.findTestedKpsByStudentAndSubject("S001", "数学"))
                .thenReturn(List.of(createTestedRow("kp1", "配方法")));

        // 模拟 exam_record 得分率
        ExamRecordDO mockRecord = new ExamRecordDO();
        mockRecord.setId(1L);
        mockRecord.setStudentNo("S001");
        mockRecord.setScoreDetails("[{\"kpNames\":\"配方法\",\"rawScore\":\"5\",\"maxScore\":\"10\"}]");
        when(examRecordRepository.findByStudentNo("S001"))
                .thenReturn(List.of(mockRecord));

        when(queryGraphRepository.findPrerequisitesUpstream(anyList(), eq(2)))
                .thenReturn(Collections.emptyList());

        PrunedSubgraph result = strategy.prune(new PruningRequest(
                "CLASS_WEAKNESS_OVERVIEW", "初三(1)班", "数学",
                Map.of("weakThreshold", 0.6, "maxHops", 2.0)));

        assertFalse(result.meta().mastersAvailable());
    }

    // ======================== 非预期意图 ========================

    @Test
    void shouldReturnUnsupportedIntentForWrongIntent() {
        PrunedSubgraph result = strategy.prune(new PruningRequest(
                "STUDENT_DIAGNOSIS", "S001", "数学", Map.of()));

        assertEquals("UNSUPPORTED_INTENT", result.meta().strategy());
        assertTrue(result.nodes().isEmpty());
    }

    // ======================== 辅助方法 ========================

    private Map<String, Object> createMastersRow(String studentNo, String studentName,
                                                  String kpId, String kpName, double weight) {
        Map<String, Object> row = new HashMap<>();
        row.put("studentNo", studentNo);
        row.put("studentName", studentName);
        row.put("kpId", kpId);
        row.put("kpName", kpName);
        row.put("weight", weight);
        return row;
    }

    private Map<String, Object> createTestedRow(String kpId, String kpName) {
        Map<String, Object> row = new HashMap<>();
        row.put("kpId", kpId);
        row.put("kpName", kpName);
        return row;
    }
}