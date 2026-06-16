package com.graphnexus.application.graph.fusion.strategy;

import com.graphnexus.application.graph.fusion.model.TestedRecord;
import com.graphnexus.application.graph.fusion.model.WeightResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 简单算术平均策略 — 测试桩，用于验证策略可替换性（AC-12）。
 *
 * <p>无视时间因素，所有考试取算术平均：Σ(rawScore/maxScore) / n。
 * 缺考（rawScore = null）跳过。空记录返回 weight = 0.0。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
@Component("simple-average")
public class SimpleAverageStrategy implements WeightCalculationStrategy {

    @Override
    public WeightResult calculate(List<TestedRecord> records) {
        if (records == null || records.isEmpty()) {
            return new WeightResult(0.0, "{\"examCount\": 0}");
        }

        double sum = 0.0;
        int count = 0;
        for (TestedRecord r : records) {
            if (r.rawScore() == null || r.maxScore() == null || r.maxScore() == 0) {
                continue; // 缺考跳过
            }
            sum += r.rawScore() / r.maxScore();
            count++;
        }

        double weight = count > 0 ? sum / count : 0.0;
        String summaryJson = String.format("{\"examCount\": %d}", count);
        return new WeightResult(weight, summaryJson);
    }

    @Override
    public String getName() {
        return "simple-average";
    }
}