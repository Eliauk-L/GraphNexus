package com.graphnexus.application.evaluation.graph.builder.nlp;

import com.graphnexus.application.evaluation.graph.config.EvaluationProperties;
import com.graphnexus.application.evaluation.graph.core.CandidateGraphNormalizer;
import com.graphnexus.application.evaluation.graph.core.TopicNameNormalizer;
import com.graphnexus.application.evaluation.graph.model.GraphBuildContext;
import com.graphnexus.application.evaluation.graph.model.GraphBuildMethod;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NlpGraphBuilderTest {
    @Test
    void sameInputProducesDeterministicGraphWithoutTokenUsage() {
        EvaluationProperties properties = new EvaluationProperties();
        SentenceSplitter splitter = new SentenceSplitter();
        RuleBasedNerExtractor ner = new RuleBasedNerExtractor(new MathTermCandidateExtractor(),
                new CandidateScorer(properties), new TopicTypeClassifier(), properties);
        NlpGraphBuilder builder = new NlpGraphBuilder(splitter, ner,
                new RuleBasedRelationExtractor(properties),
                new CandidateGraphNormalizer(new TopicNameNormalizer()));
        GraphBuildContext context = new GraphBuildContext(1L, "测试", "数学", "八年级",
                "方程是含有未知数的等式。\n\n一元一次方程是只含一个未知数的方程。\n\n先方程再一元一次方程。",
                "hash", "v1", "run");

        var first = builder.build(context);
        var second = builder.build(context);

        assertThat(builder.method()).isEqualTo(GraphBuildMethod.NLP_NER_RE);
        assertThat(first.rawGraph()).isEqualTo(second.rawGraph());
        assertThat(first.rawGraph().topics()).extracting(topic -> topic.name())
                .contains("方程", "一元一次方程");
        assertThat(first.rawGraph().dependencies()).isNotEmpty();
        assertThat(first.estimatedInputTokens()).isZero();
        assertThat(first.callCount()).isZero();
    }
}
