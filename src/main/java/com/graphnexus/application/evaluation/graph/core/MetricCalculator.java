package com.graphnexus.application.evaluation.graph.core;

import com.graphnexus.application.evaluation.graph.model.CandidateGraph;
import com.graphnexus.application.evaluation.graph.model.DependencyMatchResult;
import com.graphnexus.application.evaluation.graph.model.EvaluationMetrics;
import com.graphnexus.application.evaluation.graph.model.GoldDataset;
import com.graphnexus.application.evaluation.graph.model.TopicMatch;
import com.graphnexus.application.evaluation.graph.model.TopicMatchResult;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class MetricCalculator {

    public EvaluationMetrics calculate(GoldDataset gold, CandidateGraph candidate, TopicMatchResult topics,
                                       DependencyMatchResult dependencies, double structureValidity) {
        int strictTp = topics.strictMatches().size();
        int relaxedTp = topics.relaxedMatches().size();
        double strictP = divide(strictTp, candidate.topics().size());
        double strictR = divide(strictTp, gold.topics().size());
        double relaxedP = divide(relaxedTp, candidate.topics().size());
        double relaxedR = divide(relaxedTp, gold.topics().size());

        Map<String, com.graphnexus.application.evaluation.graph.model.CandidateTopic> candidateById = new HashMap<>();
        candidate.topics().forEach(topic -> candidateById.put(topic.tempId(), topic));
        int typed = 0, typeCorrect = 0, domained = 0, domainCorrect = 0;
        for (TopicMatch match : topics.strictMatches()) {
            var topic = candidateById.get(match.candidateTempId());
            if (topic != null && topic.type() != null && !topic.type().isBlank()) {
                typed++;
                if (match.typeMatched()) typeCorrect++;
            }
            if (topic != null && topic.domain() != null && !topic.domain().isBlank()) {
                domained++;
                if (match.domainMatched()) domainCorrect++;
            }
        }
        double typeAccuracy = divide(typeCorrect, typed);
        double domainAccuracy = divide(domainCorrect, domained);
        double relationP = divide(dependencies.truePositiveCount(), candidate.dependencies().size());
        double relationR = divide(dependencies.truePositiveCount(), gold.internalDependencies().size());
        double strictF1 = f1(strictP, strictR);
        double relationF1 = f1(relationP, relationR);
        double quality = 0.40 * strictF1 + 0.45 * relationF1 + 0.10 * typeAccuracy + 0.05 * structureValidity;
        return new EvaluationMetrics(gold.topics().size(), candidate.topics().size(), strictTp,
                strictP, strictR, strictF1, relaxedP, relaxedR, f1(relaxedP, relaxedR),
                typeAccuracy, divide(typed, strictTp), domainAccuracy, divide(domained, strictTp),
                gold.internalDependencies().size(), candidate.dependencies().size(), dependencies.truePositiveCount(),
                relationP, relationR, relationF1,
                divide(dependencies.correctlyDirectedPairCount(), dependencies.comparablePairCount()),
                divide(dependencies.correctStrengthCount(), dependencies.strengthComparableCount()),
                structureValidity, quality);
    }

    public double divide(int numerator, int denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    public double f1(double precision, double recall) {
        return precision + recall == 0 ? 0.0 : 2 * precision * recall / (precision + recall);
    }
}
