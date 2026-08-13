package com.graphnexus.application.mastery.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.application.mastery.model.ExamKnowledgeEvidence;
import com.graphnexus.application.mastery.model.QuestionEvidence;
import com.graphnexus.infrastructure.mysql.file.entity.ExamRecordDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将一条学生考试记录按知识点聚合为得分率和题目证据。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExamKnowledgeScoreAggregator {

    private final ObjectMapper objectMapper;

    public List<ExamKnowledgeEvidence> aggregate(ExamRecordDO record) {
        if (record == null || record.getScoreDetails() == null || record.getScoreDetails().isBlank()) {
            return Collections.emptyList();
        }
        Map<String, ScoreAccumulator> accumulators = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> details = objectMapper.readValue(
                    record.getScoreDetails(), new TypeReference<>() {});
            for (Map<String, Object> detail : details) {
                Double rawScore = number(detail.get("rawScore"));
                Double maxScore = number(detail.get("maxScore"));
                if (rawScore == null || maxScore == null || maxScore <= 0) continue;
                String questionLabel = string(detail.get("questionLabel"));
                for (String kpName : knowledgePointNames(detail)) {
                    accumulators.computeIfAbsent(kpName, ignored -> new ScoreAccumulator())
                            .add(questionLabel, rawScore, maxScore);
                }
            }
        } catch (Exception exception) {
            log.warn("聚合成绩明细失败: studentNo={}, examNo={}, error={}",
                    record.getStudentNo(), record.getExamNo(), exception.getMessage());
            return Collections.emptyList();
        }

        return accumulators.entrySet().stream()
                .map(entry -> new ExamKnowledgeEvidence(
                        record.getStudentNo(), record.getExamNo(), record.getExamDate(),
                        record.getSubject(), entry.getKey(), entry.getValue().scoreRate(),
                        entry.getValue().questions))
                .toList();
    }

    private List<String> knowledgePointNames(Map<String, Object> detail) {
        Object value = detail.get("kpNames");
        if (value == null) value = detail.get("kpName");
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).map(String::trim)
                    .filter(name -> !name.isBlank()).distinct().toList();
        }
        if (value instanceof String text) {
            return List.of(text.split(";")).stream().map(String::trim)
                    .filter(name -> !name.isBlank()).distinct().toList();
        }
        return List.of();
    }

    private Double number(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private static class ScoreAccumulator {
        private double rawScore;
        private double maxScore;
        private final List<QuestionEvidence> questions = new ArrayList<>();

        void add(String questionLabel, double raw, double max) {
            rawScore += raw;
            maxScore += max;
            questions.add(new QuestionEvidence(questionLabel, raw, max));
        }

        double scoreRate() {
            return maxScore == 0 ? 0 : rawScore / maxScore;
        }
    }
}
