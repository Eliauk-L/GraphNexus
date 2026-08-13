package com.graphnexus.application.evaluation.graph.builder.nlp;

import com.graphnexus.application.evaluation.graph.config.EvaluationProperties;
import com.graphnexus.application.evaluation.graph.model.CandidateTopic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedRelationExtractorTest {
    @Test
    void explicitFirstThenTriggerKeepsDirectionAndNoTriggerProducesNothing() {
        List<CandidateTopic> topics = List.of(
                new CandidateTopic("a", "方程", null, null, null),
                new CandidateTopic("b", "一元一次方程", null, null, null));
        SentenceSplitter splitter = new SentenceSplitter();
        RuleBasedRelationExtractor extractor = new RuleBasedRelationExtractor(new EvaluationProperties());
        var explicit = extractor.extract(splitter.split("先方程再一元一次方程。"), topics);
        var absent = extractor.extract(splitter.split("方程和一元一次方程都很重要。"), topics);
        assertThat(explicit).singleElement().satisfies(edge -> {
            assertThat(edge.prerequisiteTempId()).isEqualTo("a");
            assertThat(edge.topicTempId()).isEqualTo("b");
        });
        assertThat(absent).isEmpty();
    }
}
