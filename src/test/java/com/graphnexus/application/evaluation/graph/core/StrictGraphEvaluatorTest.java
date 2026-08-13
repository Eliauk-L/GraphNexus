package com.graphnexus.application.evaluation.graph.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.application.evaluation.graph.config.EvaluationProperties;
import com.graphnexus.application.evaluation.graph.gold.DatasetRegistry;
import com.graphnexus.application.evaluation.graph.gold.GoldDatasetLoader;
import com.graphnexus.application.evaluation.graph.gold.GoldDatasetValidator;
import com.graphnexus.application.evaluation.graph.model.CandidateDependency;
import com.graphnexus.application.evaluation.graph.model.CandidateGraph;
import com.graphnexus.application.evaluation.graph.model.CandidateTopic;
import com.graphnexus.application.evaluation.graph.model.GoldDataset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("严格图谱评测内核")
class StrictGraphEvaluatorTest {

    private GoldDataset gold;
    private StrictGraphEvaluator evaluator;

    @BeforeEach
    void setUp() {
        EvaluationProperties properties = new EvaluationProperties();
        properties.setDatasetRoot("pep-math-taxonomy/data");
        EvaluationProperties.DatasetConfig config = new EvaluationProperties.DatasetConfig();
        config.setDirectory("8-down");
        properties.getDatasets().put("v1", config);
        gold = new GoldDatasetLoader(new DatasetRegistry(properties), new ObjectMapper(), new GoldDatasetValidator()).load("v1");
        TopicNameNormalizer nameNormalizer = new TopicNameNormalizer();
        evaluator = new StrictGraphEvaluator(new CandidateGraphNormalizer(nameNormalizer),
                new TopicMatcher(nameNormalizer, properties), new DependencyMatcher(),
                new GraphStructureAnalyzer(), new MetricCalculator());
    }

    @Test
    @DisplayName("黄金知识点及17条内部关系作为候选时 F1 均为1")
    void perfectCandidateScoresOne() {
        CandidateGraph candidate = candidateFromGold(false);

        StrictGraphEvaluator.Outcome result = evaluator.evaluate(gold, candidate);

        assertThat(result.metrics().matchedTopicCount()).isEqualTo(19);
        assertThat(result.metrics().matchedDependencyCount()).isEqualTo(17);
        assertThat(result.metrics().topicStrictF1()).isEqualTo(1.0);
        assertThat(result.metrics().internalRelationF1()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("关系反向不会被计为正确")
    void reversedEdgesDoNotMatch() {
        StrictGraphEvaluator.Outcome result = evaluator.evaluate(gold, candidateFromGold(true));

        assertThat(result.metrics().topicStrictF1()).isEqualTo(1.0);
        assertThat(result.metrics().matchedDependencyCount()).isLessThan(17);
        assertThat(result.metrics().internalRelationF1()).isLessThan(1.0);
    }

    @Test
    @DisplayName("空候选图安全返回0且不产生NaN")
    void emptyCandidateReturnsZero() {
        StrictGraphEvaluator.Outcome result = evaluator.evaluate(gold, new CandidateGraph(List.of(), List.of()));

        assertThat(result.metrics().topicStrictF1()).isZero();
        assertThat(result.metrics().internalRelationF1()).isZero();
    }

    @Test
    @DisplayName("重复候选 tempId 被明确拒绝")
    void duplicateTempIdIsRejected() {
        CandidateGraph duplicate = new CandidateGraph(List.of(
                new CandidateTopic("same", "知识点A", null, null, null),
                new CandidateTopic("same", "知识点B", null, null, null)), List.of());

        assertThatThrownBy(() -> evaluator.evaluate(gold, duplicate))
                .hasMessageContaining("tempId 重复");
    }

    private CandidateGraph candidateFromGold(boolean reverse) {
        List<CandidateTopic> topics = gold.topics().stream().map(topic ->
                new CandidateTopic(topic.id(), topic.name(), topic.type(), topic.domain(), topic.description())).toList();
        List<CandidateDependency> edges = gold.internalDependencies().stream().map(edge -> reverse
                ? new CandidateDependency(edge.topicId(), edge.prerequisiteId(), edge.strength())
                : new CandidateDependency(edge.prerequisiteId(), edge.topicId(), edge.strength())).toList();
        return new CandidateGraph(topics, edges);
    }
}
