package com.graphnexus.application.file.core.pipeline;

import com.graphnexus.application.file.parse.model.FileParseType;
import com.graphnexus.application.file.upload.model.GradeUploadResultBO;
import com.graphnexus.application.file.upload.service.GradeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * CSV 成绩处理 Pipeline — 封装成绩文件上传全链路。
 *
 * <p>委托给既有 {@link GradeService}，保持现有 CSV 处理逻辑不变。</p>
 *
 * @author Jay
 * @date 2026/06/18
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GradeProcessingPipeline implements FileProcessingPipeline {

    private final GradeService gradeService;

    @Override
    public FileParseType supportedType() {
        return FileParseType.CSV_GRADE;
    }

    @Override
    public Object process(MultipartFile file, String subject) {
        GradeUploadResultBO bo = gradeService.uploadGradeCsv(file, subject);
        log.info("GradePipeline 处理完成: examNo={}, students={}", bo.getExamNo(), bo.getStudentCount());
        return bo;
    }

    @Override
    public Object retry(Long documentId) {
        throw new UnsupportedOperationException("CSV 成绩文件不支持重试，请重新上传");
    }
}