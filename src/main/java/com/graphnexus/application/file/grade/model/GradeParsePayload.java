package com.graphnexus.application.file.grade.model;

import java.time.LocalDate;
import java.util.List;

/**
 * 成绩文件解析结果统一载体（CSV 和 Excel 共用）。
 *
 * <p>新增成绩文件格式时，Parser 输出此 payload 即可复用全链路。</p>
 *
 * @author Jay
 * @date 2026/06/19
 */
public record GradeParsePayload(
        String examNo,
        String examName,
        LocalDate examDate,
        String subject,
        List<StudentRecord> students,
        int questionCount,
        List<String> knowledgePoints
) {

    /**
     * 单个学生的成绩记录。
     */
    public record StudentRecord(
            String studentNo,
            String name,
            String className,
            Integer totalScore,
            Integer classRank,
            List<ScoreDetail> scoreDetails
    ) {}

    /**
     * 单题得分明细。
     */
    public record ScoreDetail(
            String questionLabel,
            List<String> kpNames,
            Integer rawScore,
            Integer maxScore
    ) {}
}