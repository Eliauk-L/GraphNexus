package com.graphnexus.infrastructure.mysql.query.repository;

import com.graphnexus.infrastructure.mysql.query.entity.QueryTaskDO;
import com.graphnexus.infrastructure.mysql.query.entity.QueryTaskStatus;
import jakarta.persistence.criteria.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QueryTaskRepository JpaSpecificationExecutor 集成测试。
 *
 * <p>直连 podman MySQL（dev profile），验证动态 Specification 筛选各条件独立生效。</p>
 *
 * @author Jay
 * @date 2026/06/22
 */
@DataJpaTest
@ActiveProfiles("dev")
@DisplayName("QueryTaskRepository 动态筛选测试")
class QueryTaskRepositoryTest {

    @Autowired
    private QueryTaskRepository repository;

    @BeforeEach
    void setUp() {
        // 清理旧数据
        repository.deleteAll();

        LocalDateTime now = LocalDateTime.now();
        // 记录 1：张三 · 数学 · COMPLETED
        repository.save(QueryTaskDO.builder()
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
                .createTime(now.minusDays(1))
                .updateTime(now.minusDays(1))
                .build());

        // 记录 2：张三 · 物理 · FAILED
        repository.save(QueryTaskDO.builder()
                .taskId(UUID.randomUUID().toString())
                .question("分析学生张三的物理薄弱点")
                .studentName("张三")
                .studentNo("S2024001")
                .subject("物理")
                .status(QueryTaskStatus.FAILED)
                .intent("STUDENT_DIAGNOSIS")
                .errorMessage("LLM API 调用超时")
                .elapsedMs(35000L)
                .retryCount(2)
                .createTime(now.minusHours(2))
                .updateTime(now.minusHours(2))
                .build());

        // 记录 3：李四 · 数学 · COMPLETED
        repository.save(QueryTaskDO.builder()
                .taskId(UUID.randomUUID().toString())
                .question("李四的数学掌握度如何")
                .studentName("李四")
                .studentNo("S2024002")
                .subject("数学")
                .status(QueryTaskStatus.COMPLETED)
                .intent("STUDENT_DIAGNOSIS")
                .answer("## 分析报告\n\n李四的掌握度...")
                .tokenUsageJson("{\"estimatedTokens\":400}")
                .elapsedMs(2500L)
                .retryCount(0)
                .createTime(now)
                .updateTime(now)
                .build()).getTaskId();
    }

    @Test
    @DisplayName("按 status 精确筛选")
    void shouldFilterByStatus() {
        Specification<QueryTaskDO> spec = (root, query, cb) ->
                cb.equal(root.get("status"), QueryTaskStatus.COMPLETED);

        List<QueryTaskDO> results = repository.findAll(spec);
        assertEquals(2, results.size(), "应返回 2 条 COMPLETED 记录");
        assertTrue(results.stream().allMatch(r -> r.getStatus() == QueryTaskStatus.COMPLETED));
    }

    @Test
    @DisplayName("按 subject 精确筛选")
    void shouldFilterBySubject() {
        Specification<QueryTaskDO> spec = (root, query, cb) ->
                cb.equal(root.get("subject"), "数学");

        List<QueryTaskDO> results = repository.findAll(spec);
        assertEquals(2, results.size(), "应返回 2 条数学记录");
        assertTrue(results.stream().allMatch(r -> "数学".equals(r.getSubject())));
    }

    @Test
    @DisplayName("按 studentName 模糊筛选")
    void shouldFilterByStudentNameLike() {
        Specification<QueryTaskDO> spec = (root, query, cb) ->
                cb.like(root.get("studentName"), "%张%");

        List<QueryTaskDO> results = repository.findAll(spec);
        assertEquals(2, results.size(), "应返回 2 条张姓学生记录");
        assertTrue(results.stream().allMatch(r -> r.getStudentName().contains("张")));
    }

    @Test
    @DisplayName("按时间范围筛选")
    void shouldFilterByTimeRange() {
        LocalDateTime start = LocalDateTime.now().minusDays(2);
        LocalDateTime end = LocalDateTime.now().minusMinutes(30);

        Specification<QueryTaskDO> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), start));
            predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), end));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<QueryTaskDO> results = repository.findAll(spec);
        assertEquals(2, results.size(), "应返回 2 条在时间范围内的记录（排除 now() 创建的李四）");
    }

    @Test
    @DisplayName("组合条件筛选")
    void shouldFilterByCombinedConditions() {
        Specification<QueryTaskDO> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("subject"), "数学"));
            predicates.add(cb.equal(root.get("status"), QueryTaskStatus.COMPLETED));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<QueryTaskDO> results = repository.findAll(spec);
        assertEquals(2, results.size(), "应返回张三+李四的 COMPLETED 数学记录");
    }

    @Test
    @DisplayName("无匹配条件返回空列表")
    void shouldReturnEmptyForNoMatch() {
        Specification<QueryTaskDO> spec = (root, query, cb) ->
                cb.equal(root.get("subject"), "化学");

        List<QueryTaskDO> results = repository.findAll(spec);
        assertTrue(results.isEmpty(), "化学无记录，应返回空列表");
    }
}