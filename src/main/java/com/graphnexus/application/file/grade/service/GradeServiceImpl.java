package com.graphnexus.application.file.grade.service;

import com.graphnexus.application.file.grade.event.GradeDeletedEvent;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 成绩业务服务实现 — 条件查询 + 级联删除。
 *
 * <p>上传由 {@link GradeUploadService} 独立负责。
 * 不再直接调用 MinIO 或 Neo4j，图谱清理由事件监听器完成。</p>
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

            // 始终过滤已删除
            predicates.add(cb.equal(root.get("isDeleted"), 0));

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
    @Transactional
    public Object[] deleteByExamNo(String examNo) {
        // C2 幂等
        List<ExamRecordDO> records = examRecordRepository.findByExamNoAndIsDeleted(examNo, 0);
        if (records.isEmpty()) {
            log.info("考试 {} 无未删除记录，幂等返回", examNo);
            return new Object[]{examNo, 0};
        }

        int recordCount = records.size();

        // 物理删除
        examRecordRepository.deleteAll(records);
        log.info("考试 {} MySQL 物理删除完成，共 {} 条", examNo, recordCount);

        // 发布事件 — 图谱清理由 GradeGraphEventListener 完成
        eventPublisher.publishEvent(new GradeDeletedEvent(this, examNo, recordCount));

        return new Object[]{examNo, recordCount};
    }
}