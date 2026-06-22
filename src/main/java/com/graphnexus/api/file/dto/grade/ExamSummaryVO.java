package com.graphnexus.api.file.dto.grade;

import com.graphnexus.application.file.grade.model.ExamSummaryBO;
import lombok.Builder;

import java.time.LocalDate;

/**
 * 考试汇总 VO —— 管理考试弹窗列表用。
 *
 * @author Jay
 * @date 2026/06/22
 */
@Builder
public record ExamSummaryVO(
        String examNo,
        String examName,
        LocalDate examDate,
        String subject,
        long studentCount
) {
    public static ExamSummaryVO from(ExamSummaryBO bo) {
        return ExamSummaryVO.builder()
                .examNo(bo.examNo())
                .examName(bo.examName())
                .examDate(bo.examDate())
                .subject(bo.subject())
                .studentCount(bo.studentCount())
                .build();
    }
}