package com.graphnexus.application.analysis.fusion.model;

/**
 * 权重计算结果 — 由 {@code WeightCalculationStrategy} 产出。
 *
 * @param weight     掌握度（0~1）
 * @param summaryJson 摘要 JSON（考试次数、最近日期、各次得分率等）
 * @author Jay
 * @date 2026/06/15
 */
public record WeightResult(
        double weight,
        String summaryJson
) {}