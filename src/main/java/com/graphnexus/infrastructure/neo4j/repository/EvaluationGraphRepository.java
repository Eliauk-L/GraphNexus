package com.graphnexus.infrastructure.neo4j.repository;

import com.graphnexus.infrastructure.neo4j.repository.model.EvaluationGraphSnapshot;
import org.neo4j.driver.types.Node;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Neo4j persistence for graphs isolated by graphId. */
@Repository
public class EvaluationGraphRepository {

    private final Neo4jClient neo4jClient;
    private final TransactionTemplate neo4jTransaction;

    public EvaluationGraphRepository(Neo4jClient neo4jClient,
                                     @Qualifier("neo4jTransactionManager") PlatformTransactionManager transactionManager) {
        this.neo4jClient = neo4jClient;
        this.neo4jTransaction = new TransactionTemplate(transactionManager);
    }

    public void save(EvaluationGraphSnapshot snapshot) {
        neo4jTransaction.executeWithoutResult(ignored -> saveInTransaction(snapshot));
    }

    private void saveInTransaction(EvaluationGraphSnapshot snapshot) {
        EvaluationGraphSnapshot.MetricSnapshot metric = snapshot.metrics();
        Map<String, Object> graph = new HashMap<>();
        graph.put("id", snapshot.graphId());
        graph.put("datasetVersion", snapshot.datasetVersion());
        graph.put("datasetHash", snapshot.datasetHash());
        graph.put("method", snapshot.method());
        graph.put("status", snapshot.status());
        graph.put("createdAt", snapshot.createdAt().toString());
        graph.put("warnings", snapshot.warnings());
        graph.put("goldTopicCount", metric.goldTopicCount());
        graph.put("candidateTopicCount", metric.candidateTopicCount());
        graph.put("matchedTopicCount", metric.matchedTopicCount());
        graph.put("topicStrictPrecision", metric.topicStrictPrecision());
        graph.put("topicStrictRecall", metric.topicStrictRecall());
        graph.put("topicStrictF1", metric.topicStrictF1());
        graph.put("topicRelaxedPrecision", metric.topicRelaxedPrecision());
        graph.put("topicRelaxedRecall", metric.topicRelaxedRecall());
        graph.put("topicRelaxedF1", metric.topicRelaxedF1());
        graph.put("typeAccuracy", metric.typeAccuracy());
        graph.put("typeCoverage", metric.typeCoverage());
        graph.put("domainAccuracy", metric.domainAccuracy());
        graph.put("domainCoverage", metric.domainCoverage());
        graph.put("goldInternalDependencyCount", metric.goldInternalDependencyCount());
        graph.put("candidateDependencyCount", metric.candidateDependencyCount());
        graph.put("matchedDependencyCount", metric.matchedDependencyCount());
        graph.put("internalRelationPrecision", metric.internalRelationPrecision());
        graph.put("internalRelationRecall", metric.internalRelationRecall());
        graph.put("internalRelationF1", metric.internalRelationF1());
        graph.put("directionAccuracy", metric.directionAccuracy());
        graph.put("strengthAccuracy", metric.strengthAccuracy());
        graph.put("structureValidity", metric.structureValidity());
        graph.put("qualityScore", metric.qualityScore());

        neo4jClient.query("MERGE (g:EvaluationGraph {id: $id}) SET g = $graph")
                .bindAll(Map.of("id", snapshot.graphId(), "graph", graph)).run();
        neo4jClient.query("MATCH (:EvaluationGraph {id: $graphId})-[:CONTAINS]->(old:EvalKnowledgePoint) DETACH DELETE old")
                .bind(snapshot.graphId()).to("graphId").run();

        List<Map<String, Object>> topics = snapshot.topics().stream().map(topic -> {
            Map<String, Object> row = new HashMap<>();
            row.put("id", nodeId(snapshot.graphId(), topic.tempId()));
            row.put("tempId", topic.tempId());
            row.put("name", value(topic.name()));
            row.put("type", value(topic.type()));
            row.put("domain", value(topic.domain()));
            row.put("description", value(topic.description()));
            return row;
        }).toList();
        neo4jClient.query("UNWIND $topics AS topic "
                        + "MERGE (k:EvalKnowledgePoint {id: topic.id}) SET k += topic, k.evaluationGraphId = $graphId "
                        + "WITH k MATCH (g:EvaluationGraph {id: $graphId}) MERGE (g)-[:CONTAINS]->(k)")
                .bindAll(Map.of("graphId", snapshot.graphId(), "topics", topics)).run();

        List<Map<String, Object>> edges = snapshot.dependencies().stream().map(edge -> Map.<String, Object>of(
                "sourceId", nodeId(snapshot.graphId(), edge.prerequisiteTempId()),
                "targetId", nodeId(snapshot.graphId(), edge.topicTempId()),
                "strength", value(edge.strength()))).toList();
        neo4jClient.query("UNWIND $edges AS edge "
                        + "MATCH (a:EvalKnowledgePoint {id: edge.sourceId}), (b:EvalKnowledgePoint {id: edge.targetId}) "
                        + "MERGE (a)-[r:EVAL_PREREQUISITE_OF {evaluationGraphId: $graphId}]->(b) "
                        + "SET r.strength = edge.strength")
                .bindAll(Map.of("graphId", snapshot.graphId(), "edges", edges)).run();
    }

    public Optional<EvaluationGraphSnapshot> findByGraphId(String graphId) {
        Optional<Node> graphNode = neo4jClient.query("MATCH (g:EvaluationGraph {id: $graphId}) RETURN g")
                .bind(graphId).to("graphId").fetchAs(Node.class).mappedBy((typeSystem, record) -> record.get("g").asNode()).one();
        if (graphNode.isEmpty()) return Optional.empty();
        Node g = graphNode.get();

        Collection<Map<String, Object>> topicRows = neo4jClient.query(
                        "MATCH (:EvaluationGraph {id: $graphId})-[:CONTAINS]->(k:EvalKnowledgePoint) RETURN k ORDER BY k.tempId")
                .bind(graphId).to("graphId").fetch().all();
        List<EvaluationGraphSnapshot.TopicSnapshot> topics = topicRows.stream().map(row -> {
            Node k = (Node) row.get("k");
            return new EvaluationGraphSnapshot.TopicSnapshot(string(k, "tempId"), string(k, "name"),
                    string(k, "type"), string(k, "domain"), string(k, "description"));
        }).toList();

        Collection<Map<String, Object>> edgeRows = neo4jClient.query(
                        "MATCH (a:EvalKnowledgePoint {evaluationGraphId: $graphId})-[r:EVAL_PREREQUISITE_OF {evaluationGraphId: $graphId}]->(b:EvalKnowledgePoint) "
                                + "RETURN a.tempId AS source, b.tempId AS target, r.strength AS strength ORDER BY source, target")
                .bind(graphId).to("graphId").fetch().all();
        List<EvaluationGraphSnapshot.DependencySnapshot> edges = edgeRows.stream().map(row ->
                new EvaluationGraphSnapshot.DependencySnapshot(value(row.get("source")), value(row.get("target")), value(row.get("strength")))).toList();

        return Optional.of(new EvaluationGraphSnapshot(graphId, string(g, "datasetVersion"), string(g, "datasetHash"),
                string(g, "method"), string(g, "status"), LocalDateTime.parse(string(g, "createdAt")),
                readMetrics(g), topics, edges, readStringList(g, "warnings")));
    }

    public void saveEvaluationGraph(EvaluationGraphSnapshot snapshot) { save(snapshot); }

    public Optional<EvaluationGraphSnapshot> findEvaluationGraph(String graphId) { return findByGraphId(graphId); }

    public boolean existsByGraphId(String graphId) {
        return neo4jClient.query("MATCH (g:EvaluationGraph {id: $graphId}) RETURN count(g) > 0 AS exists")
                .bind(graphId).to("graphId").fetchAs(Boolean.class)
                .mappedBy((typeSystem, record) -> record.get("exists").asBoolean()).one().orElse(false);
    }

    public List<EvaluationGraphSnapshot.TopicSnapshot> listTopics(String graphId) {
        return findByGraphId(graphId).map(EvaluationGraphSnapshot::topics).orElse(List.of());
    }

    public List<EvaluationGraphSnapshot.DependencySnapshot> listDependencies(String graphId) {
        return findByGraphId(graphId).map(EvaluationGraphSnapshot::dependencies).orElse(List.of());
    }

    public void deleteEvaluationGraph(String graphId) {
        neo4jTransaction.executeWithoutResult(ignored -> {
            neo4jClient.query("MATCH (:EvaluationGraph {id: $graphId})-[:CONTAINS]->(k:EvalKnowledgePoint) DETACH DELETE k")
                    .bind(graphId).to("graphId").run();
            neo4jClient.query("MATCH (g:EvaluationGraph {id: $graphId}) DETACH DELETE g")
                    .bind(graphId).to("graphId").run();
        });
    }

    private EvaluationGraphSnapshot.MetricSnapshot readMetrics(Node g) {
        return new EvaluationGraphSnapshot.MetricSnapshot(
                integer(g, "goldTopicCount"), integer(g, "candidateTopicCount"), integer(g, "matchedTopicCount"),
                decimal(g, "topicStrictPrecision"), decimal(g, "topicStrictRecall"), decimal(g, "topicStrictF1"),
                decimal(g, "topicRelaxedPrecision"), decimal(g, "topicRelaxedRecall"), decimal(g, "topicRelaxedF1"),
                decimal(g, "typeAccuracy"), decimal(g, "typeCoverage"),
                decimal(g, "domainAccuracy"), decimal(g, "domainCoverage"),
                integer(g, "goldInternalDependencyCount"), integer(g, "candidateDependencyCount"), integer(g, "matchedDependencyCount"),
                decimal(g, "internalRelationPrecision"), decimal(g, "internalRelationRecall"), decimal(g, "internalRelationF1"),
                decimal(g, "directionAccuracy"), decimal(g, "strengthAccuracy"),
                decimal(g, "structureValidity"), decimal(g, "qualityScore"));
    }

    private String nodeId(String graphId, String tempId) { return graphId + ":topic:" + tempId; }
    private String value(Object value) { return value == null ? "" : value.toString(); }
    private String string(Node node, String key) { return node.containsKey(key) && !node.get(key).isNull() ? node.get(key).asString() : ""; }
    private int integer(Node node, String key) { return node.get(key).asInt(0); }
    private double decimal(Node node, String key) { return node.get(key).asDouble(0.0); }
    private List<String> readStringList(Node node, String key) {
        if (!node.containsKey(key) || node.get(key).isNull()) return List.of();
        return new ArrayList<>(node.get(key).asList(value -> value.asString()));
    }
}
