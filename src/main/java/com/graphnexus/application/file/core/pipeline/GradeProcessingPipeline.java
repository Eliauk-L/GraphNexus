package com.graphnexus.application.file.core.pipeline;

import com.graphnexus.application.file.parse.model.FileParseType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * CSV 成绩处理 Pipeline — 仅占位，成绩无后续处理链路。
 *
 * <p>成绩上传由 {@link com.graphnexus.application.file.core.upload.GradeUploadService} 负责，
 * 直接委托 {@link com.graphnexus.application.file.upload.service.GradeService} 完成。</p>
 *
 * @author Jay
 * @date 2026/06/18
 */
@Slf4j
@Component
public class GradeProcessingPipeline implements FileProcessingPipeline {

    @Override
    public FileParseType supportedType() {
        return FileParseType.CSV_GRADE;
    }

    @Override
    public Object processStored(Long documentId) {
        throw new UnsupportedOperationException("CSV 成绩文件不支持单独处理，请重新上传");
    }
}