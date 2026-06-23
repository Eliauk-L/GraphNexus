package com.graphnexus.application.file.grade.service;

import com.graphnexus.application.file.grade.model.GradeUploadResultBO;
import com.graphnexus.application.file.grade.model.GradeParsePayload;
import com.graphnexus.application.file.grade.model.GradeParsePayload.StudentRecord;
import com.graphnexus.application.file.grade.event.GradeUploadedEvent;
import com.graphnexus.application.file.parse.model.FileParseRequest;
import com.graphnexus.application.file.parse.FileParser;
import com.graphnexus.application.file.parse.FileParserRegistry;
import com.graphnexus.application.file.upload.UploadService;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.mysql.file.entity.ExamRecordDO;
import com.graphnexus.infrastructure.mysql.file.repository.ExamRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 成绩文件上传服务 — 解析（无事务）→ MySQL 短事务 → 事务外发布事件。
 *
 * <p>实现 {@link UploadService}，自包含上传链路。
 * 不直接调用 MinIO 或 Neo4j，图谱构建由事件监听器完成。</p>
 *
 * <p><b>事务策略（ADR-028）</b>：DB 操作（去重 + 批量写入）通过
 * {@link TransactionTemplate} 在短事务中执行，事件在事务外发布。</p>
 *
 * @author Jay
 * @date 2026/06/19
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GradeUploadService implements UploadService {

    private final ExamRecordRepository examRecordRepository;
    private final FileParserRegistry fileParserRegistry;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager txManager;

    @Override
    public Object upload(MultipartFile file, String subject, Long uploadedBy) {
        // ===== 阶段 1：文件解析（无事务 · ADR-028 规则 2）=====

        byte[] rawBytes;
        try {
            rawBytes = file.getBytes();
        } catch (Exception e) {
            log.error("读取文件失败", e);
            throw new BusinessException(ErrorCode.A0011, "文件读取失败");
        }

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        FileParser parser = fileParserRegistry.getParser(filename, FileParser.BIZ_GRADE)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0011,
                        "不支持的文件格式，请上传 .csv / .xlsx / .xls 成绩文件"));

        GradeParsePayload payload;
        try (InputStream is = new ByteArrayInputStream(rawBytes)) {
            var request = new FileParseRequest(is, filename, subject, rawBytes);
            var result = parser.parse(request);
            payload = (GradeParsePayload) result.payload();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件解析失败", e);
            throw new BusinessException(ErrorCode.A0011, "文件解析失败: " + e.getMessage());
        }

        String fileType = parser.supportedType().name();

        // ===== 阶段 2：构建 Record 列表（纯内存，无事务）=====

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
                    .build();
            records.add(rec);
        }

        // ===== 阶段 3：短事务内 DB 操作（ADR-028 规则 1）=====

        TransactionTemplate txTemplate = new TransactionTemplate(txManager);
        txTemplate.executeWithoutResult(status -> {
            if (examRecordRepository.existsByExamNo(payload.examNo())) {
                throw new BusinessException(ErrorCode.A0022,
                        "考试编号 " + payload.examNo() + " 已存在，请先删除再重新上传");
            }
            examRecordRepository.saveAll(records);
        });

        log.info("成绩上传完成: examNo={}, fileType={}, students={}, questions={}, kps={}",
                payload.examNo(), fileType, payload.students().size(),
                payload.questionCount(), payload.knowledgePoints().size());

        // ===== 阶段 4：事务外发布事件（ADR-028 规则 4）=====

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
                .fileType(fileType)
                .build();
    }
}