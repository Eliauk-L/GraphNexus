package com.graphnexus.application.analysis.fusion.strategy;

import com.graphnexus.application.analysis.fusion.model.TestedRecord;
import com.graphnexus.application.analysis.fusion.model.WeightResult;

import java.util.List;

/**
 * 权重计算策略接口 — 从多条考试成绩记录聚合出 MASTERS 掌握度。
 *
 * <p>实现类通过 Spring Bean 注册，由 FusionService 按 yml 配置选择。见 ADR-007。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
public interface WeightCalculationStrategy {

    /**
     * 从成绩记录列表计算聚合权重。
     *
     * @param records 该学生对某 KP 的所有考试成绩记录
     * @return weight（0~1）+ 摘要 JSON
     */
    WeightResult calculate(List<TestedRecord> records);

    /**
     * 策略标识，对应 yml 配置值。
     *
     * @return 策略名称
     */
    String getName();
}