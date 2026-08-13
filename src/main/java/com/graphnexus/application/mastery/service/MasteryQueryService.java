package com.graphnexus.application.mastery.service;

import com.graphnexus.application.mastery.model.MasteryHistoryView;
import com.graphnexus.application.mastery.model.MasteryView;
import com.graphnexus.infrastructure.mysql.mastery.entity.MasteryUpdateEventDO;
import com.graphnexus.infrastructure.mysql.mastery.repository.MasteryUpdateEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MasteryQueryService {
    private final MasteryUpdateEventRepository eventRepository;

    @Transactional(readOnly = true)
    public List<MasteryView> current(String studentNo, String subject) {
        List<MasteryUpdateEventDO> events = eventRepository
                .findByStudentNoAndSubjectOrderByKnowledgePointNameAscOccurredAtDesc(studentNo, subject);
        Map<String, MasteryUpdateEventDO> latest = new LinkedHashMap<>();
        for (MasteryUpdateEventDO event : events) {
            latest.putIfAbsent(event.getKnowledgePointId(), event);
        }
        return latest.values().stream().map(event -> new MasteryView(
                event.getKnowledgePointId(), event.getKnowledgePointName(), event.getNewWeight(),
                event.getConfidence(), event.getSampleCount(), event.getExamNo(), event.getOccurredAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MasteryHistoryView> history(String studentNo, String knowledgePointId) {
        return eventRepository
                .findByStudentNoAndKnowledgePointIdOrderByOccurredAtAscIdAsc(studentNo, knowledgePointId)
                .stream().map(event -> new MasteryHistoryView(
                        event.getEventId(), event.getExamNo(), event.getScoreRate(), event.getOldWeight(),
                        event.getNewWeight(), event.getAlpha(), event.getSampleCount(), event.getConfidence(),
                        event.getEvidenceJson(), event.getOccurredAt())).toList();
    }
}
