package com.graphnexus.api.file.dto.upload;

import com.graphnexus.application.file.grade.model.GradeUploadResultBO;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "CSV 成绩上传结果视图")
public class GradeUploadResultVO {

    @Schema(description = "考试编号（来源于 CSV 考试编号列）", example = "E20200041")
    private String examNo;

    @Schema(description = "考试名称", example = "初三数学第二次月考")
    private String examName;

    @Schema(description = "考试日期", example = "2026-06-15")
    private LocalDate examDate;

    @Schema(description = "学科", example = "数学")
    private String subject;

    @Schema(description = "导入的考生人数", example = "45")
    private int studentCount;

    @Schema(description = "试题数量", example = "20")
    private int questionCount;

    @Schema(description = "试题对应的知识点名称列表", example = "[\"二次函数图像与性质\", \"二次函数顶点式\", \"一元二次方程\"]")
    private List<String> knowledgePoints;

    @Schema(description = "CSV 文件 完整文件访问路径", example = "grades/2026/06/E20200041_scores.csv")
    private String filePath;

    @Schema(description = "CSV 文件内容 MD5（用于判重）", example = "d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9")
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
                .filePath(bo.getFilePath())
                .csvMd5(bo.getCsvMd5())
                .build();
    }
}