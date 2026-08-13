package com.graphnexus.application.evaluation.graph.core;

import com.graphnexus.application.evaluation.graph.model.CandidateDependency;
import com.graphnexus.application.evaluation.graph.model.CandidateGraph;
import com.graphnexus.application.evaluation.graph.model.DependencyMatchResult;
import com.graphnexus.application.evaluation.graph.model.EvaluationErrorDetail;
import com.graphnexus.application.evaluation.graph.model.EvaluationErrorType;
import com.graphnexus.application.evaluation.graph.model.GoldDataset;
import com.graphnexus.application.evaluation.graph.model.GoldDependency;
import com.graphnexus.application.evaluation.graph.model.TopicMatch;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class DependencyMatcher {

    public DependencyMatchResult match(CandidateGraph candidate, GoldDataset gold, List<TopicMatch> topicMatches) {
        Map<String, String> aligned = new HashMap<>();
        topicMatches.forEach(match -> aligned.put(match.candidateTempId(), match.goldTopicId()));
        Map<EdgeKey, GoldDependency> goldEdges = new HashMap<>();
        gold.internalDependencies().forEach(edge -> goldEdges.put(new EdgeKey(edge.prerequisiteId(), edge.topicId()), edge));
        Set<EdgeKey> truePositive = new HashSet<>();
        List<EvaluationErrorDetail> errors = new ArrayList<>();
        int comparablePairs = 0;
        int correctDirections = 0;
        int strengthComparable = 0;
        int correctStrength = 0;
        for (CandidateDependency edge : candidate.dependencies()) {
            String source = aligned.get(edge.prerequisiteTempId());
            String target = aligned.get(edge.topicTempId());
            String candidateId = edge.prerequisiteTempId() + "->" + edge.topicTempId();
            if (source == null || target == null) {
                errors.add(new EvaluationErrorDetail(EvaluationErrorType.UNMATCHED_DEPENDENCY_ENDPOINT,
                        candidateId, null, "候选依赖至少一个端点未匹配黄金知识点"));
                continue;
            }
            EdgeKey key = new EdgeKey(source, target);
            GoldDependency exact = goldEdges.get(key);
            GoldDependency reverse = goldEdges.get(new EdgeKey(target, source));
            if (exact != null) {
                comparablePairs++;
                correctDirections++;
                truePositive.add(key);
                if (nonBlank(edge.strength()) && nonBlank(exact.strength())) {
                    strengthComparable++;
                    if (edge.strength().equalsIgnoreCase(exact.strength())) correctStrength++;
                    else errors.add(new EvaluationErrorDetail(EvaluationErrorType.WRONG_DEPENDENCY_STRENGTH,
                            candidateId, source + "->" + target, "候选依赖强度与黄金集不一致"));
                }
            } else if (reverse != null) {
                comparablePairs++;
                errors.add(new EvaluationErrorDetail(EvaluationErrorType.REVERSED_DEPENDENCY,
                        candidateId, target + "->" + source, "候选依赖方向与黄金集相反"));
            } else {
                errors.add(new EvaluationErrorDetail(EvaluationErrorType.FALSE_POSITIVE_DEPENDENCY,
                        candidateId, null, "黄金内部关系中不存在该候选依赖"));
            }
        }
        for (EdgeKey key : goldEdges.keySet()) {
            if (!truePositive.contains(key)) {
                errors.add(new EvaluationErrorDetail(EvaluationErrorType.FALSE_NEGATIVE_DEPENDENCY,
                        null, key.source() + "->" + key.target(), "黄金内部依赖未被候选图命中"));
            }
        }
        return new DependencyMatchResult(truePositive.size(), comparablePairs, correctDirections,
                strengthComparable, correctStrength, errors);
    }

    private boolean nonBlank(String value) { return value != null && !value.isBlank(); }
    private record EdgeKey(String source, String target) { }
}
