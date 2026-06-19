package com.graphnexus.application.file.grade.service;

import com.graphnexus.application.file.grade.model.GradeRecordBO;
import com.graphnexus.application.file.grade.model.GradeUploadResultBO;
import com.graphnexus.infrastructure.neo4j.repository.GraphNodeRepository;
import com.graphnexus.infrastructure.storage.FileStorageService;
import com.graphnexus.infrastructure.mysql.file.entity.ExamRecordDO;
import com.graphnexus.infrastructure.mysql.file.repository.ExamRecordRepository;
import com.graphnexus.application.file.textbook.model.DeleteResultBO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 成绩业务服务实现 — 仅含查询与删除。
 *
 * <p>上传由 {@link GradeUploadService} 独立负责。
 * 删除链路严格遵循全局删除约束（C1-C5），使用 is_deleted 中间状态。
 * 见 DESIGN §2.1 + §2.1a + D10。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GradeServiceImpl implements GradeService {

    private final ExamRecordRepository examRecordRepository;
    private final GraphNodeRepository graphNodeRepository;
    private final FileStorageService fileStorageService;

    // ======================== 查询 ========================

    @Override
    @Transactional(readOnly = true)
    public List<GradeRecordBO> queryByExam(String examNo) {
        List<ExamRecordDO> records = examRecordRepository.findByExamNoAndIsDeleted(examNo, 0);
        return records.stream().map(r -> GradeRecordBO.builder()
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
        ).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<GradeUploadResultBO> listExams(int pageNum, int pageSize) {
        Page<Object[]> rows = examRecordRepository.findDistinctExams(
                PageRequest.of(pageNum - 1, pageSize));
        return rows.map(row -> {
            GradeUploadResultBO bo = new GradeUploadResultBO();
            bo.setExamNo((String) row[0]);
            bo.setExamName((String) row[1]);
            bo.setExamDate(row[2] != null ? ((java.sql.Date) row[2]).toLocalDate() : null);
            bo.setSubject((String) row[3]);
            bo.setFilePath((String) row[4]);
            bo.setCsvMd5((String) row[5]);
            bo.setStudentCount(((Number) row[6]).intValue());
            return bo;
        });
    }

    // ======================== 删除 ========================

    @Override
    @Transactional
    public DeleteResultBO deleteByExamNo(String examNo) {
        // C2 幂等：查询未删除的记录
        List<ExamRecordDO> records = examRecordRepository.findByExamNoAndIsDeleted(examNo, 0);
        if (records.isEmpty()) {
            log.info("考试 {} 无未删除记录，幂等返回（C2）", examNo);
            return DeleteResultBO.builder()
                    .examNo(examNo)
                    .deletedRecordCount(0)
                    .filePath(null)
                    .deletedEdgeCount(0)
                    .build();
        }

        String filePath = records.get(0).getCsvFilePath();
        int recordCount = records.size();

        // C3 中间状态：标记 is_deleted = 1（MySQL 事务保障）
        for (ExamRecordDO rec : records) {
            rec.markDeleted();
        }
        examRecordRepository.saveAll(records);
        log.info("考试 {} 已标记中间状态 is_deleted=1（C3），共 {} 条", examNo, recordCount);

        // C4 ① MinIO 文件删除（幂等）
        if (filePath != null) {
            try {
                fileStorageService.deleteFile(filePath);
            } catch (Exception e) {
                log.warn("MinIO 文件删除失败（C2 容忍），将记 WARN 继续: path={}, error={}",
                        filePath, e.getMessage());
            }
        }

        // C4 ② Neo4j 边删除（幂等，先 ATTENDED 后 TESTED）
        int attendEdges = graphNodeRepository.deleteEdgesByExamNo(examNo, "ATTENDED");
        int testedEdges = graphNodeRepository.deleteEdgesByExamNo(examNo, "TESTED");

        // C4 ③ Neo4j Exam 节点删除（DETACH DELETE 兜底）
        int deletedNodes = graphNodeRepository.deleteExamNode(examNo);

        // C4 ④ MySQL 物理删除
        examRecordRepository.deleteAll(records);

        int totalEdges = attendEdges + testedEdges;
        log.info("考试 {} 级联删除完成: MySQL={}条, MinIO={}, Neo4j边={}, Neo4j节点={}（C1-C5）",
                examNo, recordCount, filePath, totalEdges, deletedNodes);

        return DeleteResultBO.builder()
                .examNo(examNo)
                .deletedRecordCount(recordCount)
                .filePath(filePath)
                .deletedEdgeCount(totalEdges)
                .build();
    }
}
