package com.graphnexus.application.mastery.service;

import com.graphnexus.infrastructure.mysql.mastery.entity.MasteryUpdateEventDO;
import com.graphnexus.infrastructure.mysql.mastery.repository.MasteryUpdateEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MasteryQueryServiceTest {
    @Mock MasteryUpdateEventRepository repository;

    @Test
    void currentKeepsLatestEventPerKnowledgePoint() {
        MasteryQueryService service = new MasteryQueryService(repository);
        when(repository.findByStudentNoAndSubjectOrderByKnowledgePointNameAscOccurredAtDesc("S001", "数学"))
                .thenReturn(List.of(event("new", "E2", 0.7, 2), event("old", "E1", 0.5, 1)));

        var current = service.current("S001", "数学");

        assertEquals(1, current.size());
        assertEquals(0.7, current.get(0).weight(), 1e-8);
        assertEquals("E2", current.get(0).lastExamNo());
    }

    @Test
    void historyMapsEvidenceInChronologicalOrder() {
        MasteryQueryService service = new MasteryQueryService(repository);
        when(repository.findByStudentNoAndKnowledgePointIdOrderByOccurredAtAscIdAsc("S001", "kp-axis"))
                .thenReturn(List.of(event("old", "E1", 0.5, 1), event("new", "E2", 0.7, 2)));

        var history = service.history("S001", "kp-axis");

        assertEquals(List.of("E1", "E2"), history.stream().map(item -> item.examNo()).toList());
        assertEquals("[]", history.get(0).evidenceJson());
    }

    private MasteryUpdateEventDO event(String eventId, String examNo, double weight, int samples) {
        return MasteryUpdateEventDO.builder().eventId(eventId).studentNo("S001").examNo(examNo)
                .subject("数学").knowledgePointId("kp-axis").knowledgePointName("对称轴")
                .scoreRate(weight).oldWeight(samples == 1 ? null : 0.5).newWeight(weight).alpha(0.3)
                .sampleCount(samples).confidence(0.5).evidenceJson("[]")
                .occurredAt(LocalDateTime.of(2026, samples, 1, 0, 0))
                .createTime(LocalDateTime.now()).build();
    }
}
