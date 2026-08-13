package com.graphnexus.application.mastery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.infrastructure.mysql.file.entity.ExamRecordDO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExamKnowledgeScoreAggregatorTest {

    private final ExamKnowledgeScoreAggregator aggregator =
            new ExamKnowledgeScoreAggregator(new ObjectMapper());

    @Test
    void aggregatesMultipleQuestionsByKnowledgePoint() {
        ExamRecordDO record = record("["
                + "{\"questionLabel\":\"题1\",\"kpNames\":[\"对称轴\"],\"rawScore\":3,\"maxScore\":5},"
                + "{\"questionLabel\":\"题2\",\"kpNames\":[\"对称轴\",\"顶点坐标\"],\"rawScore\":5,\"maxScore\":5}"
                + "]");

        var evidence = aggregator.aggregate(record);

        assertEquals(2, evidence.size());
        var axis = evidence.stream().filter(item -> "对称轴".equals(item.knowledgePointName()))
                .findFirst().orElseThrow();
        assertEquals(0.8, axis.scoreRate(), 1e-8);
        assertEquals(2, axis.questions().size());
        var vertex = evidence.stream().filter(item -> "顶点坐标".equals(item.knowledgePointName()))
                .findFirst().orElseThrow();
        assertEquals(1.0, vertex.scoreRate(), 1e-8);
    }

    @Test
    void skipsAbsentAndInvalidScores() {
        ExamRecordDO record = record("["
                + "{\"questionLabel\":\"题1\",\"kpNames\":[\"对称轴\"],\"rawScore\":null,\"maxScore\":null},"
                + "{\"questionLabel\":\"题2\",\"kpNames\":[\"顶点坐标\"],\"rawScore\":2,\"maxScore\":0}"
                + "]");
        assertTrue(aggregator.aggregate(record).isEmpty());
    }

    @Test
    void supportsLegacySemicolonKnowledgePointNames() {
        ExamRecordDO record = record("["
                + "{\"questionLabel\":\"题1\",\"kpNames\":\"对称轴;顶点坐标\",\"rawScore\":4,\"maxScore\":5}"
                + "]");
        assertEquals(2, aggregator.aggregate(record).size());
    }

    private ExamRecordDO record(String scoreDetails) {
        return ExamRecordDO.builder()
                .studentNo("S001").examNo("E001").examDate(LocalDate.of(2026, 8, 1))
                .subject("数学").scoreDetails(scoreDetails).build();
    }
}
