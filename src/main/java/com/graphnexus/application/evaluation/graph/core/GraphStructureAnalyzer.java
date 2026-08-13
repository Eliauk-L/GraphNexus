package com.graphnexus.application.evaluation.graph.core;

import com.graphnexus.application.evaluation.graph.model.CandidateDependency;
import com.graphnexus.application.evaluation.graph.model.CandidateGraph;
import com.graphnexus.application.evaluation.graph.model.CandidateGraphNormalizationResult;
import com.graphnexus.application.evaluation.graph.model.GoldDataset;
import com.graphnexus.application.evaluation.graph.model.GraphStructureMetrics;
import com.graphnexus.application.evaluation.graph.model.TopicMatch;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class GraphStructureAnalyzer {

    public GraphStructureMetrics analyze(CandidateGraphNormalizationResult normalization,
                                         GoldDataset gold, List<TopicMatch> matches) {
        CandidateGraph graph = normalization.normalizedGraph();
        Set<String> nodes = new HashSet<>();
        graph.topics().forEach(topic -> nodes.add(topic.tempId()));
        Map<String, Set<String>> directed = adjacency(nodes);
        Map<String, Set<String>> undirected = adjacency(nodes);
        Map<String, Integer> indegree = new HashMap<>();
        nodes.forEach(id -> indegree.put(id, 0));
        for (CandidateDependency edge : graph.dependencies()) {
            directed.get(edge.prerequisiteTempId()).add(edge.topicTempId());
            undirected.get(edge.prerequisiteTempId()).add(edge.topicTempId());
            undirected.get(edge.topicTempId()).add(edge.prerequisiteTempId());
            indegree.compute(edge.topicTempId(), (ignored, value) -> value + 1);
        }
        int isolated = (int) nodes.stream().filter(id -> undirected.get(id).isEmpty()).count();
        List<Integer> components = componentSizes(nodes, undirected);
        int largest = components.stream().mapToInt(Integer::intValue).max().orElse(0);
        int cycleNodes = kahnResidual(nodes, directed, indegree);
        boolean dag = cycleNodes == 0;
        double reachability = goldReachability(gold, matches, directed);
        boolean clean = normalization.removedSelfLoops() == 0
                && normalization.removedDuplicateEdges() == 0
                && normalization.removedDanglingEdges() == 0;
        double validity = dag && clean ? 1.0 : 0.0;
        return new GraphStructureMetrics(normalization.removedSelfLoops(), normalization.mergedDuplicateTopics(),
                normalization.removedDuplicateEdges(), normalization.removedDanglingEdges(),
                divide(isolated, nodes.size()), components.size(), divide(largest, nodes.size()),
                cycleNodes, dag, reachability, validity);
    }

    private Map<String, Set<String>> adjacency(Set<String> nodes) {
        Map<String, Set<String>> result = new HashMap<>();
        nodes.forEach(id -> result.put(id, new HashSet<>()));
        return result;
    }

    private List<Integer> componentSizes(Set<String> nodes, Map<String, Set<String>> graph) {
        Set<String> visited = new HashSet<>();
        List<Integer> sizes = new ArrayList<>();
        for (String start : nodes) {
            if (!visited.add(start)) continue;
            int size = 0;
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                String node = queue.remove();
                size++;
                for (String next : graph.get(node)) if (visited.add(next)) queue.add(next);
            }
            sizes.add(size);
        }
        return sizes;
    }

    private int kahnResidual(Set<String> nodes, Map<String, Set<String>> graph, Map<String, Integer> sourceIndegree) {
        Map<String, Integer> indegree = new HashMap<>(sourceIndegree);
        ArrayDeque<String> queue = new ArrayDeque<>();
        indegree.forEach((id, degree) -> { if (degree == 0) queue.add(id); });
        int visited = 0;
        while (!queue.isEmpty()) {
            String node = queue.remove();
            visited++;
            for (String next : graph.get(node)) {
                int degree = indegree.compute(next, (ignored, value) -> value - 1);
                if (degree == 0) queue.add(next);
            }
        }
        return nodes.size() - visited;
    }

    private double goldReachability(GoldDataset gold, List<TopicMatch> matches, Map<String, Set<String>> graph) {
        Map<String, String> candidateByGold = new HashMap<>();
        matches.forEach(match -> candidateByGold.put(match.goldTopicId(), match.candidateTempId()));
        int reachable = 0;
        for (var edge : gold.internalDependencies()) {
            String source = candidateByGold.get(edge.prerequisiteId());
            String target = candidateByGold.get(edge.topicId());
            if (source != null && target != null && reachable(source, target, graph)) reachable++;
        }
        return divide(reachable, gold.internalDependencies().size());
    }

    private boolean reachable(String source, String target, Map<String, Set<String>> graph) {
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(source);
        visited.add(source);
        while (!queue.isEmpty()) {
            String node = queue.remove();
            for (String next : graph.getOrDefault(node, Set.of())) {
                if (next.equals(target)) return true;
                if (visited.add(next)) queue.add(next);
            }
        }
        return false;
    }

    private double divide(int numerator, int denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }
}
