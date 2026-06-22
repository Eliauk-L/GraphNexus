package com.graphnexus.application.file.grade.model;

import lombok.Builder;

import java.time.LocalDate;

/**
 * 考试汇总 BO —— 不重复的考试元数据（按 examNo 分组）。
 *
 * @param examNo       考试编号
 * @param examName     考试名称
 * @param examDate     考试日期
 * @param subject      学科
 * @param studentCount 该场考试的考生人数
 * @author Jay
 * @date 2026/06/22
 */
@Builder
public record ExamSummaryBO(
        String examNo,
        String examName,
        LocalDate examDate,
        String subject,
        long studentCount
) {}