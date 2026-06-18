package com.graphnexus.api.file.dto.upload;

import com.graphnexus.application.file.upload.model.GradeRecordBO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条成绩记录 VO（L1 返回前端，对应 AC-4 响应体）。
 *
 * @author Jay
 * @date 2026/06/15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "单条学生成绩记录视图")
public class GradeRecordVO {

    @Schema(description = "成绩记录 MySQL ID", example = "1")
    private Long id;

    @Schema(description = "学号", example = "20240001")
    private String studentNo;

    @Schema(description = "学生姓名", example = "张三")
    private String name;

    @Schema(description = "班级名称", example = "初三（1）班")
    private String className;

    @Schema(description = "考试编号", example = "E20200041")
    private String examNo;

    @Schema(description = "考试名称", example = "初三数学第二次月考")
    private String examName;

    @Schema(description = "学科", example = "数学")
    private String subject;

    @Schema(description = "总分", example = "85")
    private Integer totalScore;

    @Schema(description = "班级排名", example = "5")
    private Integer classRank;

    @Schema(description = "各题得分明细（JSON 格式，含每题 raw_score/max_score 和对应知识点）", example = "[{\"qno\":1,\"score\":\"10/10\",\"kp\":\"二次函数图像与性质\"},{\"qno\":2,\"score\":\"8/10\",\"kp\":\"二次函数顶点式;对称轴\"}]")
    private String scoreDetails;

    public static GradeRecordVO from(GradeRecordBO bo) {
        return GradeRecordVO.builder()
                .id(bo.getId())
                .studentNo(bo.getStudentNo())
                .name(bo.getName())
                .className(bo.getClassName())
                .examNo(bo.getExamNo())
                .examName(bo.getExamName())
                .subject(bo.getSubject())
                .totalScore(bo.getTotalScore())
                .classRank(bo.getClassRank())
                .scoreDetails(bo.getScoreDetails())
                .build();
    }
}