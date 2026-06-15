package com.graphnexus.api.document.dto;

import com.graphnexus.application.document.model.GradeRecordBO;
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
public class GradeRecordVO {

    private Long id;
    private String studentNo;
    private String name;
    private String className;
    private String examNo;
    private String examName;
    private String subject;
    private Integer totalScore;
    private Integer classRank;
    private String scoreDetails; // JSON

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