package com.graphnexus.application.evaluation.graph.builder.nlp;

import com.graphnexus.application.evaluation.graph.builder.GraphBuilder;
import com.graphnexus.application.evaluation.graph.core.CandidateGraphNormalizer;
import com.graphnexus.application.evaluation.graph.model.CandidateGraph;
import com.graphnexus.application.evaluation.graph.model.GraphBuildContext;
import com.graphnexus.application.evaluation.graph.model.GraphBuildMethod;
import com.graphnexus.application.evaluation.graph.model.GraphBuildResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class NlpGraphBuilder implements GraphBuilder {

    private final SentenceSplitter sentenceSplitter;
    private final RuleBasedNerExtractor nerExtractor;
    private final RuleBasedRelationExtractor relationExtractor;
    private final CandidateGraphNormalizer graphNormalizer;

    @Override
    public GraphBuildMethod method() { return GraphBuildMethod.NLP_NER_RE; }

    @Override
    public GraphBuildResult build(GraphBuildContext context) {
        long started = System.nanoTime();
        var sentences = sentenceSplitter.split(context.textSnapshot());
        var topics = nerExtractor.extract(sentences, context.subject());
        var dependencies = relationExtractor.extract(sentences, topics);
        var normalized = graphNormalizer.normalize(new CandidateGraph(topics, dependencies));
        long duration = (System.nanoTime() - started) / 1_000_000;
        return new GraphBuildResult(normalized.normalizedGraph(), duration, 0, 0,
                0, 0, 0, normalized.warnings());
    }
}
