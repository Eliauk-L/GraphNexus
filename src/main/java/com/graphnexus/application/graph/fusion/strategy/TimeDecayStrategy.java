package com.graphnexus.application.graph.fusion.strategy;

import com.graphnexus.application.graph.fusion.config.TimeDecayProperties;
import com.graphnexus.application.graph.fusion.model.TestedRecord;
import com.graphnexus.application.graph.fusion.model.WeightResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 时间衰减加权平均策略 v1 — 最近考试权重更高。
 *
 * <p>公式：weight = Σ(avgScoreRate_i × factor^monthsAgo_i) / Σ(factor^monthsAgo_i)
 * 缺考跳过，同考试多题先取平均。见 ADR-007 + DESIGN D2。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
@Component("time-decay")
@RequiredArgsConstructor
public class TimeDecayStrategy implements WeightCalculationStrategy {

    private final TimeDecayProperties timeDecayProperties;

    @Override
    public WeightResult calculate(List<TestedRecord> records) {
        if (records == null || records.isEmpty()) {
            return new WeightResult(0.0, "{\"examCount\": 0}");
        }

        // 过滤缺考
        List<TestedRecord> valid = records.stream()
                .filter(r -> r.rawScore() != null && r.maxScore() != null && r.maxScore() > 0)
                .collect(Collectors.toList());

        if (valid.isEmpty()) {
            return new WeightResult(0.0, "{\"examCount\": 0, \"reason\": \"all absent\"}");
        }

        // 按 examDate 分组，同考试内先取平均得分率
        Map<LocalDate, List<TestedRecord>> byDate = valid.stream()
                .collect(Collectors.groupingBy(TestedRecord::examDate));

        double factor = timeDecayProperties.getFactor();
        LocalDate now = LocalDate.now();

        double numerator = 0.0;
        double denominator = 0.0;
        List<Map<String, Object>> details = new ArrayList<>();
        LocalDate lastExamDate = null;

        for (var entry : byDate.entrySet()) {
            LocalDate examDate = entry.getKey();
            List<TestedRecord> sameDayRecords = entry.getValue();

            // 同考试内平均得分率
            double avgScoreRate = sameDayRecords.stream()
                    .mapToDouble(r -> r.rawScore() / r.maxScore())
                    .average()
                    .orElse(0.0);

            // 计算距今月数
            long monthsAgo = ChronoUnit.DAYS.between(examDate, now) / 30;
            double decayWeight = Math.pow(factor, monthsAgo);

            numerator += avgScoreRate * decayWeight;
            denominator += decayWeight;

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("examDate", examDate.toString());
            detail.put("scoreRate", Math.round(avgScoreRate * 1000.0) / 1000.0);
            detail.put("decayWeight", Math.round(decayWeight * 1000.0) / 1000.0);
            details.add(detail);

            if (lastExamDate == null || examDate.isAfter(lastExamDate)) {
                lastExamDate = examDate;
            }
        }

        double weight = denominator > 0 ? numerator / denominator : 0.0;

        // 构建 summary JSON
        StringBuilder json = new StringBuilder("{");
        json.append("\"examCount\": ").append(details.size());
        if (lastExamDate != null) {
            json.append(", \"lastExamDate\": \"").append(lastExamDate).append("\"");
        }
        json.append(", \"details\": [");
        for (int i = 0; i < details.size(); i++) {
            if (i > 0) json.append(", ");
            Map<String, Object> d = details.get(i);
            json.append("{");
            json.append("\"examDate\": \"").append(d.get("examDate")).append("\", ");
            json.append("\"scoreRate\": ").append(d.get("scoreRate")).append(", ");
            json.append("\"decayWeight\": ").append(d.get("decayWeight"));
            json.append("}");
        }
        json.append("]}");

        return new WeightResult(Math.round(weight * 1000.0) / 1000.0, json.toString());
    }

    @Override
    public String getName() {
        return "time-decay";
    }
}