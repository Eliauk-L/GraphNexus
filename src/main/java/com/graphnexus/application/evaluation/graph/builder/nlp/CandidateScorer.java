package com.graphnexus.application.evaluation.graph.builder.nlp;

import com.graphnexus.application.evaluation.graph.config.EvaluationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CandidateScorer {

    private final EvaluationProperties properties;

    public double score(MathTermCandidateExtractor.TermEvidence evidence) {
        double score = 0;
        if (evidence.definitionHit()) score += properties.getNlpDefinitionWeight();
        if (evidence.mathPatternHit()) score += properties.getNlpMathPatternWeight();
        score += Math.min(3, evidence.frequency()) * properties.getNlpFrequencyWeight();
        if (evidence.crossParagraphHit()) score += properties.getNlpCrossParagraphWeight();
        if (evidence.titleHit()) score -= properties.getNlpTitlePenalty();
        return Math.max(0, Math.min(1, score));
    }
}
