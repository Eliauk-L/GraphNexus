package com.graphnexus.application.file.grade.service;

import com.graphnexus.application.file.grade.event.GradeDeletedEvent;
import com.graphnexus.application.file.grade.model.ExamSummaryBO;
import com.graphnexus.application.file.grade.model.GradeRecordBO;
import com.graphnexus.common.PageResult;
import com.graphnexus.infrastructure.mysql.file.entity.ExamRecordDO;
import com.graphnexus.infrastructure.mysql.file.repository.ExamRecordRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 成绩业务服务实现 — 条件查询 + 级联删除。
 *
 * <p>上传由 {@link GradeUploadService} 独立负责。
 * 不再直接调用 MinIO 或 Neo4j，图谱清理由事件监听器完成。</p>
 *
 * <p><b>事务策略（ADR-028）</b>：删除的 DB 操作通过 {@link TransactionTemplate} 在短事务中执行，
 * 事件在事务外发布。</p>
 *
 * @author Jay
 * @date 2026/06/19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GradeServiceImpl implements GradeService {

    private final ExamRecordRepository examRecordRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager txManager;

    // ======================== 条件查询 ========================

    @Override
    @Transactional(readOnly = true)
    public PageResult<GradeRecordBO> queryByConditions(
            String examNo, String examName,
            String studentNo, String name,
            String className, String subject,
            int pageNum, int pageSize) {

        Specification<ExamRecordDO> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (examNo != null && !examNo.isBlank()) {
                predicates.add(cb.equal(root.get("examNo"), examNo.trim()));
            }
            if (examName != null && !examName.isBlank()) {
                predicates.add(cb.like(root.get("examName"), "%" + examName.trim() + "%"));
            }
            if (studentNo != null && !studentNo.isBlank()) {
                predicates.add(cb.equal(root.get("studentNo"), studentNo.trim()));
            }
            if (name != null && !name.isBlank()) {
                predicates.add(cb.like(root.get("name"), "%" + name.trim() + "%"));
            }
            if (className != null && !className.isBlank()) {
                predicates.add(cb.equal(root.get("className"), className.trim()));
            }
            if (subject != null && !subject.isBlank()) {
                predicates.add(cb.equal(root.get("subject"), subject.trim()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<ExamRecordDO> page = examRecordRepository.findAll(spec,
                PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime")));

        Page<GradeRecordBO> boPage = page.map(r -> GradeRecordBO.builder()
                .id(r.getId())
                .studentNo(r.getStudentNo())
                .name(r.getName())
                .className(r.getClassName())
                .examNo(r.getExamNo())
                .examName(r.getExamName())
                .subject(r.getSubject())
                .totalScore(r.getTotalScore())
                .classRank(r.getClassRank())
                .scoreDetails(r.getScoreDetails())
                .build()
        );

        return PageResult.of(boPage);
    }

    // ======================== 删除 ========================

    @Override
    public Object[] deleteByExamNo(String examNo) {
        // 短事务内执行 DB 操作（ADR-028 规则 1）
        TransactionTemplate txTemplate = new TransactionTemplate(txManager);
        Object[] result = txTemplate.execute(status -> {
            List<ExamRecordDO> records = examRecordRepository.findByExamNo(examNo);
            if (records.isEmpty()) {
                log.info("考试 {} 无未删除记录，幂等返回", examNo);
                return new Object[]{examNo, 0, List.of()};
            }

            int recordCount = records.size();
            // 提取涉及的学生学号（去重），用于级联检查孤点 StudentNode
            List<String> studentNos = records.stream()
                    .map(ExamRecordDO::getStudentNo)
                    .distinct()
                    .toList();

            examRecordRepository.deleteAll(records);
            log.info("考试 {} MySQL 物理删除完成，共 {} 条，涉及 {} 名学生",
                    examNo, recordCount, studentNos.size());
            return new Object[]{examNo, recordCount, studentNos};
        });

        // 事务外发布事件（ADR-028 规则 4）
        int recordCount = (int) result[1];
        if (recordCount > 0) {
            @SuppressWarnings("unchecked")
            List<String> studentNos = (List<String>) result[2];
            eventPublisher.publishEvent(new GradeDeletedEvent(
                    this, (String) result[0], recordCount, studentNos));
        }
        return result;
    }

    // ======================== 考试汇总 ========================

    @Override
    @Transactional(readOnly = true)
    public PageResult<ExamSummaryBO> listDistinctExams(int pageNum, int pageSize) {
        Page<Object[]> page = examRecordRepository.findDistinctExams(
                PageRequest.of(pageNum - 1, pageSize));

        Page<ExamSummaryBO> boPage = page.map(row -> ExamSummaryBO.builder()
                .examNo((String) row[0])
                .examName((String) row[1])
                .examDate((LocalDate) row[2])
                .subject((String) row[3])
                .studentCount(((Number) row[4]).longValue())
                .build());

        return PageResult.of(boPage);
    }
}