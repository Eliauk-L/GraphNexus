package com.graphnexus.application.graph.fusion.model;

import java.time.LocalDate;

/**
 * 成绩记录 — 从 MySQL exam_record 提取的单次考试得分数据。
 *
 * <p>rawScore 为 null 表示缺考（-/ -）。见 ADR-007。</p>
 *
 * @param examDate 考试日期
 * @param rawScore 得分（null = 缺考）
 * @param maxScore 满分
 * @param kpName   考查的知识点名称
 * @author Jay
 * @date 2026/06/15
 */
public record TestedRecord(
        LocalDate examDate,
        Double rawScore,
        Double maxScore,
        String kpName
) {}