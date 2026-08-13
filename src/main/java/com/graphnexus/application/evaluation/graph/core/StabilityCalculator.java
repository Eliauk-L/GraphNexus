package com.graphnexus.application.evaluation.graph.core;

import com.graphnexus.application.evaluation.graph.model.CandidateGraph;
import com.graphnexus.application.evaluation.graph.model.EvaluationMetrics;
import com.graphnexus.application.evaluation.graph.model.StabilityMetrics;
import com.graphnexus.application.evaluation.graph.model.StabilityRun;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

@Component
@RequiredArgsConstructor
public class StabilityCalculator {

    private final TopicNameNormalizer normalizer;

    public StabilityMetrics calculate(List<StabilityRun> runs) {
        if (runs == null || runs.isEmpty()) {
            return new StabilityMetrics(0, 0, Map.of(), Map.of(), 0, 0, 0);
        }
        double topicJaccard = averagePairwise(runs, run -> topicKeys(run.graph()));
        double dependencyJaccard = averagePairwise(runs, run -> dependencyKeys(run.graph()));
        Map<String, ToDoubleFunction<EvaluationMetrics>> fields = new LinkedHashMap<>();
        fields.put("topicStrictF1", EvaluationMetrics::topicStrictF1);
        fields.put("topicRelaxedF1", EvaluationMetrics::topicRelaxedF1);
        fields.put("internalRelationF1", EvaluationMetrics::internalRelationF1);
        fields.put("qualityScore", EvaluationMetrics::qualityScore);
        Map<String, Double> means = new LinkedHashMap<>();
        Map<String, Double> deviations = new LinkedHashMap<>();
        fields.forEach((name, extractor) -> {
            double mean = runs.stream().map(StabilityRun::metrics).mapToDouble(extractor).average().orElse(0);
            double variance = runs.stream().map(StabilityRun::metrics).mapToDouble(extractor)
                    .map(value -> (value - mean) * (value - mean)).average().orElse(0);
            means.put(name, mean);
            deviations.put(name, Math.sqrt(variance));
        });
        long min = runs.stream().mapToLong(StabilityRun::durationMs).min().orElse(0);
        long max = runs.stream().mapToLong(StabilityRun::durationMs).max().orElse(0);
        double meanDuration = runs.stream().mapToLong(StabilityRun::durationMs).average().orElse(0);
        return new StabilityMetrics(topicJaccard, dependencyJaccard, means, deviations, min, meanDuration, max);
    }

    private double averagePairwise(List<StabilityRun> runs,
                                   java.util.function.Function<StabilityRun, Set<String>> extractor) {
        if (runs.size() == 1) return 1.0;
        double total = 0;
        int pairs = 0;
        for (int i = 0; i < runs.size(); i++) {
            for (int j = i + 1; j < runs.size(); j++) {
                total += jaccard(extractor.apply(runs.get(i)), extractor.apply(runs.get(j)));
                pairs++;
            }
        }
        return pairs == 0 ? 0 : total / pairs;
    }

    private Set<String> topicKeys(CandidateGraph graph) {
        Set<String> names = new HashSet<>();
        graph.topics().forEach(topic -> names.add(normalizer.normalize(topic.name())));
        return names;
    }

    private Set<String> dependencyKeys(CandidateGraph graph) {
        Map<String, String> names = new java.util.HashMap<>();
        graph.topics().forEach(topic -> names.put(topic.tempId(), normalizer.normalize(topic.name())));
        Set<String> edges = new HashSet<>();
        graph.dependencies().forEach(edge -> edges.add(names.get(edge.prerequisiteTempId()) + "->" + names.get(edge.topicTempId())));
        return edges;
    }

    private double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() && right.isEmpty()) return 1.0;
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }
}
