package com.graphnexus.application.mastery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.application.mastery.config.MasteryProperties;
import com.graphnexus.application.mastery.strategy.EmaMasteryUpdateStrategy;
import com.graphnexus.application.mastery.strategy.MasteryUpdateStrategy;
import com.graphnexus.infrastructure.mysql.file.entity.ExamRecordDO;
import com.graphnexus.infrastructure.mysql.file.repository.ExamRecordRepository;
import com.graphnexus.infrastructure.mysql.mastery.entity.MasteryUpdateEventDO;
import com.graphnexus.infrastructure.mysql.mastery.repository.MasteryUpdateEventRepository;
import com.graphnexus.infrastructure.neo4j.repository.MasteryGraphRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MasteryUpdateServiceTest {
    @Mock ExamRecordRepository examRecordRepository;
    @Mock MasteryUpdateEventRepository eventRepository;
    @Mock MasteryGraphRepository graphRepository;

    private MasteryUpdateService service;

    @BeforeEach
    void setUp() {
        MasteryProperties properties = new MasteryProperties();
        properties.getEma().setAlpha(0.3);
        MasteryUpdateStrategy ema = new EmaMasteryUpdateStrategy(properties);
        service = new MasteryUpdateService(
                examRecordRepository, eventRepository, graphRepository,
                new ExamKnowledgeScoreAggregator(new ObjectMapper()), Map.of("ema", ema),
                properties, new ObjectMapper());
    }

    @Test
    void replaysExamsChronologicallyAndUpsertsLatestState() {
        ExamRecordDO later = record("E2", LocalDate.of(2026, 2, 1), 10, 10);
        ExamRecordDO earlier = record("E1", LocalDate.of(2026, 1, 1), 5, 10);
        when(graphRepository.findStudentNodeId("S001")).thenReturn(Optional.of("student-1"));
        when(graphRepository.findKnowledgePointId("对称轴", "数学")).thenReturn(Optional.of("kp-axis"));
        when(eventRepository.findByStudentNoAndSubjectOrderByOccurredAtAscIdAsc("S001", "数学"))
                .thenReturn(List.of());
        when(examRecordRepository.findByStudentNoAndSubject("S001", "数学"))
                .thenReturn(List.of(later, earlier));

        int created = service.rebuild("S001", "数学");

        assertEquals(2, created);
        ArgumentCaptor<MasteryUpdateEventDO> events = ArgumentCaptor.forClass(MasteryUpdateEventDO.class);
        verify(eventRepository, times(2)).save(events.capture());
        assertEquals("E1", events.getAllValues().get(0).getExamNo());
        assertEquals(0.5, events.getAllValues().get(0).getNewWeight(), 1e-8);
        assertEquals("E2", events.getAllValues().get(1).getExamNo());
        assertEquals(0.65, events.getAllValues().get(1).getNewWeight(), 1e-8);
        verify(graphRepository).upsert(eq("student-1"), eq("kp-axis"),
                any(), eq("E2"), eq(LocalDate.of(2026, 2, 1)), any());
    }

    @Test
    void removesMasteryStateWhenNoEvidenceRemains() {
        MasteryUpdateEventDO old = new MasteryUpdateEventDO();
        old.setKnowledgePointId("kp-axis");
        when(graphRepository.findStudentNodeId("S001")).thenReturn(Optional.of("student-1"));
        when(eventRepository.findByStudentNoAndSubjectOrderByOccurredAtAscIdAsc("S001", "数学"))
                .thenReturn(List.of(old));
        when(examRecordRepository.findByStudentNoAndSubject("S001", "数学"))
                .thenReturn(List.of());

        assertEquals(0, service.rebuild("S001", "数学"));
        verify(graphRepository).delete("student-1", "kp-axis");
    }

    private ExamRecordDO record(String examNo, LocalDate date, int raw, int max) {
        String details = "[{\"questionLabel\":\"题1\",\"kpNames\":[\"对称轴\"],"
                + "\"rawScore\":" + raw + ",\"maxScore\":" + max + "}]";
        return ExamRecordDO.builder().studentNo("S001").examNo(examNo).examDate(date)
                .subject("数学").scoreDetails(details).build();
    }
}
