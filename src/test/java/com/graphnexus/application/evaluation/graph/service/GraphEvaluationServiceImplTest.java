package com.graphnexus.application.evaluation.graph.service;

import com.graphnexus.application.evaluation.graph.core.StrictGraphEvaluator;
import com.graphnexus.application.evaluation.graph.gold.GoldDatasetLoader;
import com.graphnexus.application.evaluation.graph.model.CandidateGraph;
import com.graphnexus.application.evaluation.graph.model.EvaluationMetrics;
import com.graphnexus.application.evaluation.graph.model.GoldDataset;
import com.graphnexus.application.evaluation.graph.service.impl.GraphEvaluationServiceImpl;
import com.graphnexus.infrastructure.neo4j.repository.EvaluationGraphRepository;
import com.graphnexus.infrastructure.neo4j.repository.model.EvaluationGraphSnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GraphEvaluationServiceImplTest {

    @Test
    void evaluatesAndPersistsCompletedSnapshot() {
        GoldDatasetLoader loader = mock(GoldDatasetLoader.class);
        StrictGraphEvaluator evaluator = mock(StrictGraphEvaluator.class);
        EvaluationGraphRepository repository = mock(EvaluationGraphRepository.class);
        GoldDataset gold = new GoldDataset("v1", "hash", List.of(), List.of(), List.of(), List.of(), Set.of(), List.of());
        CandidateGraph graph = new CandidateGraph(List.of(), List.of());
        EvaluationMetrics metrics = new EvaluationMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        when(loader.load("v1")).thenReturn(gold);
        when(evaluator.evaluate(gold, graph)).thenReturn(new StrictGraphEvaluator.Outcome(graph, metrics, List.of()));
        GraphEvaluationServiceImpl service = new GraphEvaluationServiceImpl(loader, evaluator, repository);

        var result = service.evaluateManual("v1", graph);

        ArgumentCaptor<EvaluationGraphSnapshot> captor = ArgumentCaptor.forClass(EvaluationGraphSnapshot.class);
        verify(repository).save(captor.capture());
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.method()).isEqualTo("MANUAL");
        assertThat(captor.getValue().graphId()).isEqualTo(result.graphId());
    }
}
