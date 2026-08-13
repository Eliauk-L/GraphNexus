package com.graphnexus.application.agent.tool.profile;

import com.graphnexus.application.agent.tool.ToolExecutionContext;
import com.graphnexus.application.mastery.config.MasteryProperties;
import com.graphnexus.application.mastery.model.MasteryView;
import com.graphnexus.application.mastery.service.MasteryQueryService;
import com.graphnexus.infrastructure.mysql.file.entity.ExamRecordDO;
import com.graphnexus.infrastructure.mysql.file.repository.ExamRecordRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StudentProfileToolTest {
    @Test
    void buildsProfileAndMasteryBands() {
        ExamRecordRepository exams = mock(ExamRecordRepository.class);
        MasteryQueryService mastery = mock(MasteryQueryService.class);
        when(exams.findByStudentNoAndSubject("S001", "数学"))
                .thenReturn(List.of(exam("E2", LocalDate.of(2026, 2, 1)), exam("E1", LocalDate.of(2026, 1, 1))));
        when(mastery.current("S001", "数学")).thenReturn(List.of(
                view("weak", 0.4), view("border", 0.7), view("mastered", 0.9)));

        var result = new StudentProfileTool(exams, mastery, new MasteryProperties()).execute(
                new StudentProfileInput("S001", "数学", 1), context(Set.of("S001")));

        assertTrue(result.success());
        assertEquals(1, result.data().recentExams().size());
        assertEquals("E2", result.data().recentExams().get(0).examNo());
        assertEquals(1, result.data().weak().size());
        assertEquals(1, result.data().borderline().size());
        assertEquals(1, result.data().mastered().size());
    }

    @Test
    void deniesStudentOutsideServerScope() {
        var result = new StudentProfileTool(mock(ExamRecordRepository.class),
                mock(MasteryQueryService.class), new MasteryProperties()).execute(
                new StudentProfileInput("S999", "数学", 5), context(Set.of("S001")));
        assertFalse(result.success());
        assertEquals("STUDENT_SCOPE_DENIED", result.errorCode());
    }

    private ExamRecordDO exam(String examNo, LocalDate date) {
        return ExamRecordDO.builder().studentNo("S001").name("张三").className("九一班")
                .subject("数学").examNo(examNo).examName(examNo).examDate(date).totalScore(80).build();
    }

    private MasteryView view(String id, double weight) {
        return new MasteryView(id, id, weight, 0.7, 3, "E2", LocalDateTime.now());
    }

    private ToolExecutionContext context(Set<String> scope) {
        return new ToolExecutionContext("task", "teacher", Set.of("TEACHER"), scope, Instant.MAX, "trace");
    }
}
