package com.graphnexus.application.evaluation.graph.builder.nlp;

import com.graphnexus.application.evaluation.graph.config.EvaluationProperties;
import com.graphnexus.application.evaluation.graph.model.CandidateTopic;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RuleBasedNerExtractor {

    private final MathTermCandidateExtractor termExtractor;
    private final CandidateScorer scorer;
    private final TopicTypeClassifier typeClassifier;
    private final EvaluationProperties properties;

    public List<CandidateTopic> extract(List<SentenceSplitter.Sentence> sentences, String domain) {
        List<CandidateTopic> result = new ArrayList<>();
        int index = 0;
        for (var evidence : termExtractor.extract(sentences)) {
            if (scorer.score(evidence) < properties.getNlpTopicThreshold()) continue;
            String context = sentences.stream().filter(sentence -> sentence.text().contains(evidence.term()))
                    .map(SentenceSplitter.Sentence::text).findFirst().orElse("");
            result.add(new CandidateTopic("nlp-" + (++index), evidence.term(),
                    typeClassifier.classify(evidence.term(), context), domain, context));
        }
        return List.copyOf(result);
    }
}
