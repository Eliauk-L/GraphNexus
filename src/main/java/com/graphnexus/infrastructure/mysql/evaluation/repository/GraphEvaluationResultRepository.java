package com.graphnexus.infrastructure.mysql.evaluation.repository;

import com.graphnexus.infrastructure.mysql.evaluation.entity.GraphEvaluationResultDO;
import com.graphnexus.infrastructure.mysql.evaluation.entity.GraphEvaluationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface GraphEvaluationResultRepository extends JpaRepository<GraphEvaluationResultDO, Long> {
    List<GraphEvaluationResultDO> findByGroupIdOrderByMethodAscRepeatIndexAsc(String groupId);
    Optional<GraphEvaluationResultDO> findByGraphId(String graphId);
    List<GraphEvaluationResultDO> findByStatus(GraphEvaluationStatus status);

    @Transactional
    long deleteByGroupId(String groupId);
}
