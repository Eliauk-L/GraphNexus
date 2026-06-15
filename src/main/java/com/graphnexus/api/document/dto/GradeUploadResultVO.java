package com.graphnexus.api.document.dto;

import com.graphnexus.application.document.model.GradeUploadResultBO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 成绩上传结果 VO（L1 返回前端，对应 AC-1 响应体）。
 *
 * @author Jay
 * @date 2026/06/15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradeUploadResultVO {

    private String examNo;
    private String examName;
    private LocalDate examDate;
    private String subject;
    private int studentCount;
    private int questionCount;
    private List<String> knowledgePoints;
    private String minioPath;
    private String csvMd5;

    public static GradeUploadResultVO from(GradeUploadResultBO bo) {
        return GradeUploadResultVO.builder()
                .examNo(bo.getExamNo())
                .examName(bo.getExamName())
                .examDate(bo.getExamDate())
                .subject(bo.getSubject())
                .studentCount(bo.getStudentCount())
                .questionCount(bo.getQuestionCount())
                .knowledgePoints(bo.getKnowledgePoints())
                .minioPath(bo.getMinioPath())
                .csvMd5(bo.getCsvMd5())
                .build();
    }
}