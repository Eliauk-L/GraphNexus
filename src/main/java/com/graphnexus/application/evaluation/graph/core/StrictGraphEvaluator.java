package com.graphnexus.application.evaluation.graph.core;

import com.graphnexus.application.evaluation.graph.model.CandidateGraph;
import com.graphnexus.application.evaluation.graph.model.CandidateGraphNormalizationResult;
import com.graphnexus.application.evaluation.graph.model.DependencyMatchResult;
import com.graphnexus.application.evaluation.graph.model.EvaluationErrorDetail;
import com.graphnexus.application.evaluation.graph.model.EvaluationErrorType;
import com.graphnexus.application.evaluation.graph.model.EvaluationMetrics;
import com.graphnexus.application.evaluation.graph.model.GoldDataset;
import com.graphnexus.application.evaluation.graph.model.GraphEvaluationReport;
import com.graphnexus.application.evaluation.graph.model.GraphStructureMetrics;
import com.graphnexus.application.evaluation.graph.model.TopicMatch;
import com.graphnexus.application.evaluation.graph.model.TopicMatchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Compatibility facade for the complete M2 evaluation pipeline. */
@Component
@RequiredArgsConstructor
public class StrictGraphEvaluator {

    private final CandidateGraphNormalizer graphNormalizer;
    private final TopicMatcher topicMatcher;
    private final DependencyMatcher dependencyMatcher;
    private final GraphStructureAnalyzer structureAnalyzer;
    private final MetricCalculator metricCalculator;

    public Outcome evaluate(GoldDataset gold, CandidateGraph rawCandidate) {
        GraphEvaluationReport report = evaluateReport(gold, rawCandidate);
        return new Outcome(report.normalization().normalizedGraph(), report.metrics(),
                report.normalization().warnings(), report);
    }

    public GraphEvaluationReport evaluateReport(GoldDataset gold, CandidateGraph rawCandidate) {
        CandidateGraphNormalizationResult normalization = graphNormalizer.normalize(rawCandidate);
        CandidateGraph candidate = normalization.normalizedGraph();
        TopicMatchResult topicMatches = topicMatcher.match(candidate.topics(), gold.topics());
        DependencyMatchResult dependencyMatches = dependencyMatcher.match(candidate, gold, topicMatches.strictMatches());
        GraphStructureMetrics structure = structureAnalyzer.analyze(normalization, gold, topicMatches.strictMatches());
        EvaluationMetrics metrics = metricCalculator.calculate(gold, candidate, topicMatches,
                dependencyMatches, structure.validityScore());
        List<EvaluationErrorDetail> errors = new ArrayList<>();
        appendTopicErrors(candidate, gold, topicMatches.strictMatches(), errors);
        errors.addAll(dependencyMatches.errors());
        return new GraphEvaluationReport(normalization, topicMatches, dependencyMatches, structure, metrics, errors);
    }

    private void appendTopicErrors(CandidateGraph candidate, GoldDataset gold, List<TopicMatch> matches,
                                   List<EvaluationErrorDetail> errors) {
        Set<String> matchedCandidates = new HashSet<>();
        Set<String> matchedGold = new HashSet<>();
        for (TopicMatch match : matches) {
            matchedCandidates.add(match.candidateTempId());
            matchedGold.add(match.goldTopicId());
            if (!match.typeMatched()) errors.add(new EvaluationErrorDetail(EvaluationErrorType.WRONG_TOPIC_TYPE,
                    match.candidateTempId(), match.goldTopicId(), "候选知识点类型与黄金集不一致"));
            if (!match.domainMatched()) errors.add(new EvaluationErrorDetail(EvaluationErrorType.WRONG_TOPIC_DOMAIN,
                    match.candidateTempId(), match.goldTopicId(), "候选知识点领域与黄金集不一致"));
        }
        candidate.topics().stream().filter(topic -> !matchedCandidates.contains(topic.tempId())).forEach(topic ->
                errors.add(new EvaluationErrorDetail(EvaluationErrorType.FALSE_POSITIVE_TOPIC,
                        topic.tempId(), null, "候选知识点未严格匹配黄金集")));
        gold.topics().stream().filter(topic -> !matchedGold.contains(topic.id())).forEach(topic ->
                errors.add(new EvaluationErrorDetail(EvaluationErrorType.FALSE_NEGATIVE_TOPIC,
                        null, topic.id(), "黄金知识点未被候选图严格命中")));
    }

    public record Outcome(CandidateGraph graph, EvaluationMetrics metrics, List<String> warnings,
                          GraphEvaluationReport report) {
        public Outcome {
            warnings = List.copyOf(warnings);
        }

        /** Compatibility constructor retained for service mocks and early tests. */
        public Outcome(CandidateGraph graph, EvaluationMetrics metrics, List<String> warnings) {
            this(graph, metrics, warnings, null);
        }
    }
}
