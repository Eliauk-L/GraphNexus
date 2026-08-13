package com.graphnexus.application.mastery.model;

import java.time.LocalDate;
import java.util.List;

/** 单次考试中某学生对一个知识点的聚合得分证据。 */
public record ExamKnowledgeEvidence(
        String studentNo,
        String examNo,
        LocalDate examDate,
        String subject,
        String knowledgePointName,
        double scoreRate,
        List<QuestionEvidence> questions
) {
    public ExamKnowledgeEvidence {
        questions = List.copyOf(questions);
    }
}
