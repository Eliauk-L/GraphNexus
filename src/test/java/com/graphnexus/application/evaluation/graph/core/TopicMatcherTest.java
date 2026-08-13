package com.graphnexus.application.evaluation.graph.core;

import com.graphnexus.application.evaluation.graph.config.EvaluationProperties;
import com.graphnexus.application.evaluation.graph.model.CandidateTopic;
import com.graphnexus.application.evaluation.graph.model.GoldTopic;
import com.graphnexus.application.evaluation.graph.model.MatchType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TopicMatcherTest {

    @Test
    void strictAndRelaxedMatchesRemainOneToOne() {
        EvaluationProperties properties = new EvaluationProperties();
        properties.setRelaxedMatchThreshold(0.65);
        TopicMatcher matcher = new TopicMatcher(new TopicNameNormalizer(), properties);
        var result = matcher.match(List.of(
                new CandidateTopic("c1", "勾股定理", "CONCEPTUAL", "几何", null),
                new CandidateTopic("c2", "勾股定理的应用", "PROCEDURAL", "几何", null)), List.of(
                new GoldTopic("g1", "勾股定理", "CONCEPTUAL", "几何", null),
                new GoldTopic("g2", "勾股定理应用", "PROCEDURAL", "几何", null)));

        assertThat(result.strictMatches()).hasSize(1);
        assertThat(result.relaxedMatches()).hasSize(2);
        assertThat(result.relaxedMatches()).extracting(match -> match.goldTopicId()).doesNotHaveDuplicates();
        assertThat(result.relaxedMatches()).extracting(match -> match.candidateTempId()).doesNotHaveDuplicates();
        assertThat(result.relaxedMatches()).anyMatch(match -> match.matchType() == MatchType.RELAXED);
    }

    @Test
    void belowThresholdDoesNotMatch() {
        EvaluationProperties properties = new EvaluationProperties();
        properties.setRelaxedMatchThreshold(0.99);
        TopicMatcher matcher = new TopicMatcher(new TopicNameNormalizer(), properties);
        var result = matcher.match(List.of(new CandidateTopic("c", "完全不同", null, null, null)),
                List.of(new GoldTopic("g", "勾股定理", null, null, null)));
        assertThat(result.relaxedMatches()).isEmpty();
    }
}
