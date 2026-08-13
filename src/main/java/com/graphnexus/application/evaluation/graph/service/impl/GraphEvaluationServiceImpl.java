package com.graphnexus.application.evaluation.graph.service.impl;

import com.graphnexus.application.evaluation.graph.core.StrictGraphEvaluator;
import com.graphnexus.application.evaluation.graph.gold.GoldDatasetLoader;
import com.graphnexus.application.evaluation.graph.model.CandidateDependency;
import com.graphnexus.application.evaluation.graph.model.CandidateGraph;
import com.graphnexus.application.evaluation.graph.model.CandidateTopic;
import com.graphnexus.application.evaluation.graph.model.EvaluationMetrics;
import com.graphnexus.application.evaluation.graph.model.GoldDataset;
import com.graphnexus.application.evaluation.graph.model.GraphEvaluationResult;
import com.graphnexus.application.evaluation.graph.service.GraphEvaluationService;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.neo4j.repository.EvaluationGraphRepository;
import com.graphnexus.infrastructure.neo4j.repository.model.EvaluationGraphSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GraphEvaluationServiceImpl implements GraphEvaluationService {

    private final GoldDatasetLoader goldDatasetLoader;
    private final StrictGraphEvaluator strictGraphEvaluator;
    private final EvaluationGraphRepository evaluationGraphRepository;

    @Override
    public GraphEvaluationResult evaluateManual(String datasetVersion, CandidateGraph candidateGraph) {
        GoldDataset gold = goldDatasetLoader.load(datasetVersion);
        StrictGraphEvaluator.Outcome outcome = strictGraphEvaluator.evaluate(gold, candidateGraph);
        String graphId = UUID.randomUUID().toString();
        GraphEvaluationResult result = new GraphEvaluationResult(graphId, datasetVersion, gold.datasetHash(),
                "MANUAL", "COMPLETED", LocalDateTime.now(), outcome.metrics(), outcome.graph(),
                java.util.stream.Stream.concat(gold.warnings().stream(), outcome.warnings().stream()).toList());
        evaluationGraphRepository.save(toSnapshot(result));
        return result;
    }

    @Override
    public GraphEvaluationResult getGraph(String graphId) {
        if (graphId == null || graphId.isBlank()) {
            throw new BusinessException(ErrorCode.A0002, "graphId 不能为空");
        }
        return evaluationGraphRepository.findByGraphId(graphId)
                .map(this::fromSnapshot)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0001, "评测图谱不存在: " + graphId));
    }

    private EvaluationGraphSnapshot toSnapshot(GraphEvaluationResult result) {
        EvaluationMetrics m = result.metrics();
        return new EvaluationGraphSnapshot(result.graphId(), result.datasetVersion(), result.datasetHash(),
                result.method(), result.status(), result.createdAt(),
                new EvaluationGraphSnapshot.MetricSnapshot(m.goldTopicCount(), m.candidateTopicCount(), m.matchedTopicCount(),
                        m.topicStrictPrecision(), m.topicStrictRecall(), m.topicStrictF1(),
                        m.topicRelaxedPrecision(), m.topicRelaxedRecall(), m.topicRelaxedF1(),
                        m.typeAccuracy(), m.typeCoverage(), m.domainAccuracy(), m.domainCoverage(),
                        m.goldInternalDependencyCount(), m.candidateDependencyCount(), m.matchedDependencyCount(),
                        m.internalRelationPrecision(), m.internalRelationRecall(), m.internalRelationF1(),
                        m.directionAccuracy(), m.strengthAccuracy(), m.structureValidity(), m.qualityScore()),
                result.graph().topics().stream().map(t -> new EvaluationGraphSnapshot.TopicSnapshot(
                        t.tempId(), t.name(), t.type(), t.domain(), t.description())).toList(),
                result.graph().dependencies().stream().map(d -> new EvaluationGraphSnapshot.DependencySnapshot(
                        d.prerequisiteTempId(), d.topicTempId(), d.strength())).toList(), result.warnings());
    }

    private GraphEvaluationResult fromSnapshot(EvaluationGraphSnapshot snapshot) {
        EvaluationGraphSnapshot.MetricSnapshot m = snapshot.metrics();
        EvaluationMetrics metrics = new EvaluationMetrics(m.goldTopicCount(), m.candidateTopicCount(), m.matchedTopicCount(),
                m.topicStrictPrecision(), m.topicStrictRecall(), m.topicStrictF1(),
                m.topicRelaxedPrecision(), m.topicRelaxedRecall(), m.topicRelaxedF1(),
                m.typeAccuracy(), m.typeCoverage(), m.domainAccuracy(), m.domainCoverage(),
                m.goldInternalDependencyCount(), m.candidateDependencyCount(), m.matchedDependencyCount(),
                m.internalRelationPrecision(), m.internalRelationRecall(), m.internalRelationF1(),
                m.directionAccuracy(), m.strengthAccuracy(), m.structureValidity(), m.qualityScore());
        CandidateGraph graph = new CandidateGraph(
                snapshot.topics().stream().map(t -> new CandidateTopic(t.tempId(), t.name(), t.type(), t.domain(), t.description())).toList(),
                snapshot.dependencies().stream().map(d -> new CandidateDependency(d.prerequisiteTempId(), d.topicTempId(), d.strength())).toList());
        return new GraphEvaluationResult(snapshot.graphId(), snapshot.datasetVersion(), snapshot.datasetHash(), snapshot.method(),
                snapshot.status(), snapshot.createdAt(), metrics, graph, snapshot.warnings());
    }
}
