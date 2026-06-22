package com.graphnexus.application.query.chat.service.impl;

import com.graphnexus.api.query.dto.history.HistoryQueryRequest;
import com.graphnexus.api.query.dto.history.HistoryRecordVO;
import com.graphnexus.common.PageResult;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.infrastructure.mysql.query.entity.QueryTaskDO;
import com.graphnexus.infrastructure.mysql.query.entity.QueryTaskStatus;
import com.graphnexus.infrastructure.mysql.query.repository.QueryTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QueryServiceImpl 历史查询/导出方法单元测试。
 *
 * <p>直连 podman MySQL（dev profile），验证 queryHistory/exportSingle/exportBatch。</p>
 *
 * @author Jay
 * @date 2026/06/22
 */
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("QueryServiceImpl 历史查询导出测试")
class QueryServiceImplHistoryTest {

    @Autowired
    private QueryServiceImpl queryService;

    @Autowired
    private QueryTaskRepository repository;

    private String completedTaskId;

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        // COMPLETED 记录
        var task1 = repository.save(QueryTaskDO.builder()
                .taskId(UUID.randomUUID().toString())
                .question("分析学生张三的数学薄弱点")
                .studentName("张三")
                .studentNo("S2024001")
                .subject("数学")
                .status(QueryTaskStatus.COMPLETED)
                .intent("STUDENT_DIAGNOSIS")
                .answer("## 分析报告\n\n张三的数学薄弱点...")
                .tokenUsageJson("{\"estimatedTokens\":500}")
                .elapsedMs(3000L)
                .retryCount(0)
                .build());
        completedTaskId = task1.getTaskId();

        // FAILED 记录
        repository.save(QueryTaskDO.builder()
                .taskId(UUID.randomUUID().toString())
                .question("李四的物理掌握度如何")
                .studentName("李四")
                .studentNo("S2024002")
                .subject("物理")
                .status(QueryTaskStatus.FAILED)
                .intent("STUDENT_DIAGNOSIS")
                .errorMessage("LLM 调用超时")
                .elapsedMs(35000L)
                .retryCount(2)
                .build());
    }

    @Test
    @DisplayName("分页查询返回记录")
    void shouldReturnPaginatedHistory() {
        PageResult<HistoryRecordVO> result = queryService.queryHistory(
                new HistoryQueryRequest(null, null, null, null, null, null, 1, 10));

        assertEquals(2, result.total());
        assertEquals(2, result.list().size());
    }

    @Test
    @DisplayName("按 subject 筛选")
    void shouldFilterBySubject() {
        PageResult<HistoryRecordVO> result = queryService.queryHistory(
                new HistoryQueryRequest(null, null, "数学", null, null, null, 1, 10));

        assertEquals(1, result.total());
        assertEquals("张三", result.list().get(0).studentName());
    }

    @Test
    @DisplayName("按 status 筛选")
    void shouldFilterByStatus() {
        PageResult<HistoryRecordVO> result = queryService.queryHistory(
                new HistoryQueryRequest(null, null, null, "COMPLETED", null, null, 1, 10));

        assertEquals(1, result.total());
        assertEquals("COMPLETED", result.list().get(0).status());
    }

    @Test
    @DisplayName("按 studentName 模糊筛选")
    void shouldFilterByStudentName() {
        PageResult<HistoryRecordVO> result = queryService.queryHistory(
                new HistoryQueryRequest("张", null, null, null, null, null, 1, 10));

        assertEquals(1, result.total());
        assertTrue(result.list().get(0).studentName().contains("张"));
    }

    @Test
    @DisplayName("无匹配筛选返回空列表")
    void shouldReturnEmptyForNoMatch() {
        PageResult<HistoryRecordVO> result = queryService.queryHistory(
                new HistoryQueryRequest(null, null, "化学", null, null, null, 1, 10));

        assertEquals(0, result.total());
        assertTrue(result.list().isEmpty());
    }

    @Test
    @DisplayName("单条导出返回完整 DO（含 answer）")
    void shouldExportSingle() {
        QueryTaskDO task = queryService.exportSingle(completedTaskId);

        assertNotNull(task);
        assertEquals(completedTaskId, task.getTaskId());
        assertNotNull(task.getAnswer());
        assertTrue(task.getAnswer().contains("张三"));
    }

    @Test
    @DisplayName("单条导出不存在的 taskId 抛 A0021")
    void shouldThrowForNonexistentTaskId() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> queryService.exportSingle(UUID.randomUUID().toString()));

        assertEquals("A0021", ex.getErrorCode());
    }

    @Test
    @DisplayName("删除历史记录")
    void shouldDeleteHistory() {
        queryService.deleteHistory(completedTaskId);

        // 删除后再次查询应抛异常
        BusinessException ex = assertThrows(BusinessException.class,
                () -> queryService.exportSingle(completedTaskId));
        assertEquals("A0021", ex.getErrorCode());
    }

    @Test
    @DisplayName("删除不存在的记录抛 A0021")
    void shouldThrowForDeleteNonexistent() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> queryService.deleteHistory(UUID.randomUUID().toString()));
        assertEquals("A0021", ex.getErrorCode());
    }
}