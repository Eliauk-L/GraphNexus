package com.graphnexus.application.file.grade.service;

import com.graphnexus.application.file.grade.model.GradeRecordBO;
import com.graphnexus.application.file.grade.model.GradeUploadResultBO;
import com.graphnexus.application.file.grade.parser.CsvGradeParser.CsvParsePayload;
import com.graphnexus.application.file.grade.parser.CsvGradeParser.StudentRecord;
import com.graphnexus.infrastructure.neo4j.edge.AttendedEdge;
import com.graphnexus.infrastructure.neo4j.edge.TestedEdge;
import com.graphnexus.infrastructure.neo4j.node.ExamNode;
import com.graphnexus.infrastructure.neo4j.node.KnowledgePointNode;
import com.graphnexus.infrastructure.neo4j.node.StudentNode;
import com.graphnexus.infrastructure.neo4j.repository.GraphNodeRepository;
import com.graphnexus.infrastructure.storage.FileStorageService;
import com.graphnexus.infrastructure.mysql.file.entity.ExamRecordDO;
import com.graphnexus.infrastructure.mysql.file.repository.ExamRecordRepository;
import com.graphnexus.application.file.textbook.model.DeleteResultBO;
import com.graphnexus.application.file.parse.FileParseRequest;
import com.graphnexus.application.file.parse.FileParser;
import com.graphnexus.application.file.parse.FileParserRegistry;
import com.graphnexus.application.file.grade.event.GradeUploadedEvent;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.common.util.Md5Utils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;

/**
 * 成绩处理业务服务实现。
 *
 * <p>编排 L3 基础设施完成 CSV 上传→解析→入库→图构建全链路。
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
    private final FileParserRegistry fileParserRegistry;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    // ======================== 上传 ========================

    @Override
    @Transactional
    public GradeUploadResultBO uploadGradeCsv(MultipartFile file, String subject) {
        byte[] rawBytes;
        try {
            rawBytes = file.getBytes();
        } catch (Exception e) {
            log.error("读取 CSV 文件失败", e);
            throw new BusinessException(ErrorCode.A0011, "文件读取失败");
        }

        String csvMd5 = Md5Utils.computeMd5(rawBytes);

        // 判重
        List<ExamRecordDO> dups = examRecordRepository.findByCsvMd5AndIsDeletedFalse(csvMd5);
        if (!dups.isEmpty()) {
            String existExamNo = dups.get(0).getExamNo();
            log.info("CSV 重复上传（MD5={}），将覆盖 examNo={}", csvMd5, existExamNo);
            deleteByExamNo(existExamNo);
        }

        // 解析 CSV
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown.csv";
        CsvParsePayload payload;
        try (InputStream is = new ByteArrayInputStream(rawBytes)) {
            var request = new FileParseRequest(is, filename, subject, rawBytes);
            var result = fileParserRegistry.getParser(filename, FileParser.BIZ_GRADE)
                    .orElseThrow(() -> new BusinessException(ErrorCode.A0011, "未找到 CSV 文件解析器"))
                    .parse(request);
            payload = (CsvParsePayload) result.payload();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("CSV 解析失败", e);
            throw new BusinessException(ErrorCode.A0011, "CSV 解析失败: " + e.getMessage());
        }

        // MinIO 上传
        String filePath = "grades/" + UUID.randomUUID() + ".csv";
        try (InputStream is = new ByteArrayInputStream(rawBytes)) {
            fileStorageService.uploadFile(is, filePath, "text/csv");
        } catch (Exception e) {
            log.error("MinIO 上传失败: filePath={}", filePath, e);
            throw new BusinessException(ErrorCode.B0001, "文件上传失败");
        }

        // MySQL 批量写入
        List<ExamRecordDO> records = new ArrayList<>();
        for (StudentRecord sr : payload.students()) {
            String scoreDetailsJson;
            try {
                scoreDetailsJson = objectMapper.writeValueAsString(sr.scoreDetails());
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.B0001,
                        "成绩明细序列化失败: " + e.getMessage());
            }

            ExamRecordDO rec = ExamRecordDO.builder()
                    .studentNo(sr.studentNo())
                    .name(sr.name())
                    .className(sr.className())
                    .examNo(payload.examNo())
                    .examName(payload.examName())
                    .examDate(payload.examDate())
                    .subject(payload.subject())
                    .totalScore(sr.totalScore())
                    .classRank(sr.classRank())
                    .scoreDetails(scoreDetailsJson)
                    .csvFilePath(filePath)
                    .csvMd5(csvMd5)
                    .build();
            records.add(rec);
        }
        examRecordRepository.saveAll(records);

        // Neo4j 图构建
        // ① MERGE ExamNode
        ExamNode examNode = new ExamNode(
                payload.examNo(), payload.examName(), payload.examDate(), payload.subject()
        );
        examNode = graphNodeRepository.save(examNode);

        // ② MERGE Student → AttendedEdge → Exam
        for (StudentRecord sr : payload.students()) {
            StudentNode studentNode = new StudentNode(
                    sr.studentNo(), sr.name(), sr.className(), null
            );
            studentNode = graphNodeRepository.save(studentNode);
            graphNodeRepository.saveEdge(new AttendedEdge(studentNode.getId(), examNode.getId()));
        }

        // ③ MERGE KnowledgePoint → TestedEdge → Exam
        for (String kpName : payload.knowledgePoints()) {
            KnowledgePointNode kpNode = new KnowledgePointNode(kpName, payload.subject());
            kpNode = graphNodeRepository.save(kpNode);
            graphNodeRepository.saveEdge(new TestedEdge(examNode.getId(), kpNode.getId()));
        }

        log.info("CSV 成绩上传完成: examNo={}, students={}, questions={}, kps={}",
                payload.examNo(), payload.students().size(),
                payload.questionCount(), payload.knowledgePoints().size());

        // 发布成绩上传完成事件 — 图模块监听后触发增量融合 + 指标缓存失效
        eventPublisher.publishEvent(new GradeUploadedEvent(
                this, payload.examNo(), payload.subject(), payload.knowledgePoints()));

        return GradeUploadResultBO.builder()
                .examNo(payload.examNo())
                .examName(payload.examName())
                .examDate(payload.examDate())
                .subject(payload.subject())
                .studentCount(payload.students().size())
                .questionCount(payload.questionCount())
                .knowledgePoints(payload.knowledgePoints())
                .filePath(filePath)
                .csvMd5(csvMd5)
                .build();
    }

    // ======================== 查询 ========================

    @Override
    @Transactional(readOnly = true)
    public List<GradeRecordBO> queryByExam(String examNo) {
        List<ExamRecordDO> records = examRecordRepository.findByExamNoAndIsDeletedFalse(examNo);
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
        List<ExamRecordDO> records = examRecordRepository.findByExamNoAndIsDeletedFalse(examNo);
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