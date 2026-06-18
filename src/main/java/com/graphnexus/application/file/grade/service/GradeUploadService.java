package com.graphnexus.application.file.grade.service;

import com.graphnexus.application.file.grade.model.GradeUploadResultBO;
import com.graphnexus.application.file.upload.UploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 成绩文件上传服务 — 入库 {@code exam_record} 表。
 *
 * <p>委托给既有 {@link GradeService}，CSV 解析→MinIO→MySQL→Neo4j 全链路在 GradeService 内部完成。</p>
 *
 * @author Jay
 * @date 2026/06/18
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GradeUploadService implements UploadService {

    private final GradeService gradeService;

    @Override
    public Object upload(MultipartFile file, String subject) {
        GradeUploadResultBO bo = gradeService.uploadGradeCsv(file, subject);
        log.info("成绩已上传: examNo={}, students={}", bo.getExamNo(), bo.getStudentCount());
        return bo;
    }
}