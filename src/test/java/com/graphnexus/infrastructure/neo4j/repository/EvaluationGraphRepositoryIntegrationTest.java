package com.graphnexus.infrastructure.neo4j.repository;

import com.graphnexus.infrastructure.neo4j.repository.model.EvaluationGraphSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Explicit local integration test; opt in with -Devaluation.neo4j.it=true. */
@SpringBootTest
@ActiveProfiles("dev")
@EnabledIfSystemProperty(named = "evaluation.neo4j.it", matches = "true")
class EvaluationGraphRepositoryIntegrationTest {

    @Autowired
    private EvaluationGraphRepository repository;

    @Autowired
    private Neo4jClient neo4jClient;

    private final String graphId = "it-" + UUID.randomUUID();
    private final String otherGraphId = "it-" + UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        repository.deleteEvaluationGraph(graphId);
        repository.deleteEvaluationGraph(otherGraphId);
    }

    @Test
    void roundTripsAndKeepsGraphsIsolated() {
        EvaluationGraphSnapshot snapshot = snapshot(graphId, "LLM");

        repository.saveEvaluationGraph(snapshot);
        var loaded = repository.findEvaluationGraph(graphId);

        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow().topics()).hasSize(2);
        assertThat(loaded.orElseThrow().dependencies()).containsExactly(
                new EvaluationGraphSnapshot.DependencySnapshot("a", "b", "hard"));
        assertThat(loaded.orElseThrow().metrics().goldInternalDependencyCount()).isEqualTo(17);
        assertThat(repository.existsByGraphId(graphId)).isTrue();
        assertThat(repository.listTopics(graphId)).hasSize(2);
        assertThat(repository.listDependencies(graphId)).hasSize(1);

        repository.saveEvaluationGraph(snapshot(otherGraphId, "NLP_NER_RE"));
        assertThat(repository.listTopics(otherGraphId)).extracting(EvaluationGraphSnapshot.TopicSnapshot::name)
                .containsExactly("A", "B");

        long distinctNodeIds = neo4jClient.query(
                        "MATCH (k:EvalKnowledgePoint) WHERE k.evaluationGraphId IN $graphIds "
                                + "RETURN count(DISTINCT k.id) AS count")
                .bind(List.of(graphId, otherGraphId)).to("graphIds")
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("count").asLong()).one().orElse(-1L);
        assertThat(distinctNodeIds).isEqualTo(4);

        repository.saveEvaluationGraph(snapshot);
        assertThat(repository.listTopics(graphId)).hasSize(2);
        assertThat(repository.listDependencies(graphId)).hasSize(1);

        repository.deleteEvaluationGraph(graphId);
        assertThat(repository.existsByGraphId(graphId)).isFalse();
        assertThat(repository.existsByGraphId(otherGraphId)).isTrue();
        assertThat(repository.listTopics(otherGraphId)).hasSize(2);

        long productionKpCount = neo4jClient.query(
                        "MATCH (k:KnowledgePoint) WHERE k.evaluationGraphId IN $graphIds RETURN count(k) AS count")
                .bindAll(Map.of("graphIds", List.of(graphId, otherGraphId)))
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("count").asLong()).one().orElse(-1L);
        assertThat(productionKpCount).isZero();

        long schemaObjectCount = neo4jClient.query(
                        "SHOW INDEXES YIELD name "
                                + "WHERE name IN ['eval_graph_id', 'eval_kp_id', 'eval_kp_graph'] "
                                + "RETURN count(*) AS count")
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("count").asLong()).one().orElse(-1L);
        assertThat(schemaObjectCount).isEqualTo(3);
    }

    private EvaluationGraphSnapshot snapshot(String id, String method) {
        return new EvaluationGraphSnapshot(
                id, "it-dataset", "hash", method, "COMPLETED", LocalDateTime.now(),
                new EvaluationGraphSnapshot.MetricSnapshot(19, 2, 2, 1, 2.0 / 19, 0.2,
                        17, 1, 1, 1, 1.0 / 17, 0.1),
                List.of(
                        new EvaluationGraphSnapshot.TopicSnapshot("a", "A", "CONCEPTUAL", "math", ""),
                        new EvaluationGraphSnapshot.TopicSnapshot("b", "B", "PROCEDURAL", "math", "")),
                List.of(new EvaluationGraphSnapshot.DependencySnapshot("a", "b", "hard")),
                List.of("integration-test"));
    }
}
