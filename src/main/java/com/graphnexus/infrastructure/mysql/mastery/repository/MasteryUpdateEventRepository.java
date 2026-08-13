package com.graphnexus.infrastructure.mysql.mastery.repository;

import com.graphnexus.infrastructure.mysql.mastery.entity.MasteryUpdateEventDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MasteryUpdateEventRepository extends JpaRepository<MasteryUpdateEventDO, Long> {
    List<MasteryUpdateEventDO> findByExamNo(String examNo);

    List<MasteryUpdateEventDO> findByStudentNoAndSubjectOrderByOccurredAtAscIdAsc(
            String studentNo, String subject);

    List<MasteryUpdateEventDO> findByStudentNoAndKnowledgePointIdOrderByOccurredAtAscIdAsc(
            String studentNo, String knowledgePointId);

    List<MasteryUpdateEventDO> findByStudentNoAndSubjectOrderByKnowledgePointNameAscOccurredAtDesc(
            String studentNo, String subject);

    void deleteByStudentNoAndSubject(String studentNo, String subject);
}
