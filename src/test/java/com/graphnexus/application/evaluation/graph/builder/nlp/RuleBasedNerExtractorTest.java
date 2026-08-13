package com.graphnexus.application.evaluation.graph.builder.nlp;

import com.graphnexus.application.evaluation.graph.config.EvaluationProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedNerExtractorTest {
    @Test
    void extractsDefinitionsButFiltersGenericChapterTitle() {
        EvaluationProperties properties = new EvaluationProperties();
        CandidateScorer scorer = new CandidateScorer(properties);
        RuleBasedNerExtractor extractor = new RuleBasedNerExtractor(new MathTermCandidateExtractor(), scorer,
                new TopicTypeClassifier(), properties);
        SentenceSplitter splitter = new SentenceSplitter();

        var definitions = extractor.extract(splitter.split("方程是含有未知数的等式。"), "数学");
        var title = extractor.extract(splitter.split("第一章 一元一次方程"), "数学");

        assertThat(definitions).anyMatch(topic -> topic.name().equals("方程"));
        assertThat(title).isEmpty();
    }
}
