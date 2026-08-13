package com.graphnexus.application.mastery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.application.mastery.model.MasteryState;
import com.graphnexus.application.mastery.model.MasteryUpdateResult;
import com.graphnexus.application.mastery.strategy.MasteryUpdateStrategy;
import com.graphnexus.infrastructure.mysql.file.entity.ExamRecordDO;
import com.graphnexus.infrastructure.mysql.file.repository.ExamRecordRepository;
import com.graphnexus.infrastructure.mysql.mastery.entity.MasteryUpdateEventDO;
import com.graphnexus.infrastructure.mysql.mastery.repository.MasteryUpdateEventRepository;
import com.graphnexus.infrastructure.neo4j.repository.MasteryGraphRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 以考试记录为事实源，确定性重放学生掌握度事件并刷新 Neo4j 最新状态。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MasteryUpdateService {
    private final ExamRecordRepository examRecordRepository;
    private final MasteryUpdateEventRepository eventRepository;
    private final MasteryGraphRepository graphRepository;
    private final ExamKnowledgeScoreAggregator aggregator;
    private final Map<String, MasteryUpdateStrategy> strategies;
    private final com.graphnexus.application.mastery.config.MasteryProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional
    public int rebuildForExam(String examNo) {
        List<ExamRecordDO> records = examRecordRepository.findByExamNo(examNo);
        Set<StudentSubject> targets = new LinkedHashSet<>();
        records.forEach(record -> targets.add(new StudentSubject(record.getStudentNo(), record.getSubject())));
        // 删除事件发生在 exam_record 被删除之后，使用账本恢复受影响范围。
        eventRepository.findByExamNo(examNo).forEach(event ->
                targets.add(new StudentSubject(event.getStudentNo(), event.getSubject())));
        return targets.stream().mapToInt(target -> rebuild(target.studentNo, target.subject)).sum();
    }

    @Transactional
    public int rebuild(String studentNo, String subject) {
        MasteryUpdateStrategy strategy = strategy();
        String studentNodeId = graphRepository.findStudentNodeId(studentNo).orElse(null);
        if (studentNodeId == null) {
            log.warn("掌握度重放跳过：Student 不存在 studentNo={}", studentNo);
            return 0;
        }

        List<MasteryUpdateEventDO> oldEvents =
                eventRepository.findByStudentNoAndSubjectOrderByOccurredAtAscIdAsc(studentNo, subject);
        Set<String> oldKnowledgePointIds = oldEvents.stream()
                .map(MasteryUpdateEventDO::getKnowledgePointId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        eventRepository.deleteByStudentNoAndSubject(studentNo, subject);
        eventRepository.flush();

        List<ExamRecordDO> records = new ArrayList<>(
                examRecordRepository.findByStudentNoAndSubject(studentNo, subject));
        records.sort(Comparator.comparing(ExamRecordDO::getExamDate,
                Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(ExamRecordDO::getExamNo));

        Map<String, MasteryState> states = new LinkedHashMap<>();
        Map<String, LatestUpdate> latest = new LinkedHashMap<>();
        int created = 0;
        for (ExamRecordDO record : records) {
            for (var evidence : aggregator.aggregate(record)) {
                String kpId = graphRepository.findKnowledgePointId(
                        evidence.knowledgePointName(), subject).orElse(null);
                if (kpId == null) {
                    log.warn("掌握度重放跳过：知识点未入图 name={}, subject={}",
                            evidence.knowledgePointName(), subject);
                    continue;
                }
                MasteryState oldState = states.getOrDefault(kpId, MasteryState.empty());
                MasteryUpdateResult result = strategy.update(oldState, evidence);
                String evidenceJson = writeJson(evidence.questions());
                eventRepository.save(event(evidence, kpId, result, evidenceJson));
                states.put(kpId, new MasteryState(result.newWeight(), result.sampleCount()));
                latest.put(kpId, new LatestUpdate(result, evidence.examNo(), evidence.examDate(), evidenceJson));
                created++;
            }
        }

        for (String removedKpId : oldKnowledgePointIds) {
            if (!latest.containsKey(removedKpId)) graphRepository.delete(studentNodeId, removedKpId);
        }
        latest.forEach((kpId, update) -> graphRepository.upsert(
                studentNodeId, kpId, update.result, update.examNo, update.examDate, update.description));
        return created;
    }

    private MasteryUpdateStrategy strategy() {
        MasteryUpdateStrategy strategy = strategies.get(properties.getStrategy());
        if (strategy == null) {
            throw new IllegalStateException("未知掌握度策略: " + properties.getStrategy());
        }
        return strategy;
    }

    private MasteryUpdateEventDO event(
            com.graphnexus.application.mastery.model.ExamKnowledgeEvidence evidence,
            String kpId, MasteryUpdateResult result, String evidenceJson) {
        return MasteryUpdateEventDO.builder()
                .eventId(UUID.randomUUID().toString())
                .studentNo(evidence.studentNo()).examNo(evidence.examNo()).subject(evidence.subject())
                .knowledgePointId(kpId).knowledgePointName(evidence.knowledgePointName())
                .scoreRate(evidence.scoreRate()).oldWeight(result.oldWeight())
                .newWeight(result.newWeight()).alpha(result.alpha())
                .sampleCount(result.sampleCount()).confidence(result.confidence())
                .evidenceJson(evidenceJson)
                .occurredAt(evidence.examDate().atStartOfDay())
                .createTime(LocalDateTime.now()).build();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("掌握度证据序列化失败", exception);
        }
    }

    private record StudentSubject(String studentNo, String subject) {}
    private record LatestUpdate(MasteryUpdateResult result, String examNo,
                                java.time.LocalDate examDate, String description) {}
}
