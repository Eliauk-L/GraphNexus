package com.graphnexus.application.evaluation.graph.core;

import com.graphnexus.application.evaluation.graph.config.EvaluationProperties;
import com.graphnexus.application.evaluation.graph.model.CandidateTopic;
import com.graphnexus.application.evaluation.graph.model.GoldTopic;
import com.graphnexus.application.evaluation.graph.model.MatchType;
import com.graphnexus.application.evaluation.graph.model.TopicMatch;
import com.graphnexus.application.evaluation.graph.model.TopicMatchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class TopicMatcher {

    private final TopicNameNormalizer normalizer;
    private final EvaluationProperties properties;

    public TopicMatchResult match(List<CandidateTopic> candidates, List<GoldTopic> goldTopics) {
        Map<String, GoldTopic> goldByName = new LinkedHashMap<>();
        goldTopics.forEach(gold -> goldByName.putIfAbsent(normalizer.normalize(gold.name()), gold));
        List<TopicMatch> strict = new ArrayList<>();
        Set<String> usedCandidates = new HashSet<>();
        Set<String> usedGold = new HashSet<>();
        for (CandidateTopic candidate : candidates) {
            GoldTopic gold = goldByName.get(normalizer.normalize(candidate.name()));
            if (gold != null && usedGold.add(gold.id())) {
                strict.add(toMatch(candidate, gold, MatchType.STRICT, 1.0));
                usedCandidates.add(candidate.tempId());
            }
        }

        List<ScoredPair> pairs = new ArrayList<>();
        for (CandidateTopic candidate : candidates) {
            if (usedCandidates.contains(candidate.tempId())) continue;
            for (GoldTopic gold : goldTopics) {
                if (usedGold.contains(gold.id())) continue;
                double score = score(candidate, gold);
                if (score >= properties.getRelaxedMatchThreshold()) pairs.add(new ScoredPair(candidate, gold, score));
            }
        }
        pairs.sort(Comparator.comparingDouble(ScoredPair::score).reversed()
                .thenComparing(pair -> pair.candidate().tempId()).thenComparing(pair -> pair.gold().id()));
        List<TopicMatch> relaxed = new ArrayList<>(strict);
        for (ScoredPair pair : pairs) {
            if (usedCandidates.add(pair.candidate().tempId()) && usedGold.add(pair.gold().id())) {
                relaxed.add(toMatch(pair.candidate(), pair.gold(), MatchType.RELAXED, pair.score()));
            }
        }
        return new TopicMatchResult(strict, relaxed);
    }

    private TopicMatch toMatch(CandidateTopic candidate, GoldTopic gold, MatchType type, double score) {
        return new TopicMatch(candidate.tempId(), gold.id(), type, score,
                same(candidate.type(), gold.type()), same(candidate.domain(), gold.domain()));
    }

    private double score(CandidateTopic candidate, GoldTopic gold) {
        String left = normalizer.normalize(candidate.name());
        String right = normalizer.normalize(gold.name());
        double compatibility = (same(candidate.type(), gold.type()) ? 0.5 : 0.0)
                + (same(candidate.domain(), gold.domain()) ? 0.5 : 0.0);
        return 0.70 * ngramJaccard(left, right) + 0.20 * editSimilarity(left, right) + 0.10 * compatibility;
    }

    double ngramJaccard(String left, String right) {
        Set<String> a = ngrams(left);
        Set<String> b = ngrams(right);
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    double editSimilarity(String left, String right) {
        int max = Math.max(left.length(), right.length());
        if (max == 0) return 1.0;
        int[] previous = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1];
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            previous = current;
        }
        return 1.0 - (double) previous[right.length()] / max;
    }

    private Set<String> ngrams(String value) {
        Set<String> grams = new HashSet<>();
        if (value.length() < 2) {
            if (!value.isEmpty()) grams.add(value);
            return grams;
        }
        for (int i = 0; i < value.length() - 1; i++) grams.add(value.substring(i, i + 2));
        return grams;
    }

    private boolean same(String left, String right) {
        return left != null && !left.isBlank() && right != null && left.trim().equalsIgnoreCase(right.trim());
    }

    private record ScoredPair(CandidateTopic candidate, GoldTopic gold, double score) {
    }
}
