package com.graphnexus.application.graph.construction.validate;

import com.graphnexus.infrastructure.neo4j.edge.GraphEdge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 教材图谱写入前的确定性质量门禁。无效关系被隔离，节点仍可正常入库。 */
@Slf4j
@Component
public class GraphQualityValidator {
    private static final String PREREQUISITE = "PREREQUISITE_OF";

    public ValidationResult validate(List<? extends GraphEdge> edges, Set<String> validNodeIds) {
        List<GraphEdge> accepted = new ArrayList<>();
        List<RejectedEdge> rejected = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        Map<String, Set<String>> prerequisites = new HashMap<>();

        for (GraphEdge edge : edges) {
            String reason = invalidReason(edge, validNodeIds, identities, prerequisites);
            if (reason != null) {
                rejected.add(new RejectedEdge(edge.getSourceNodeId(), edge.getTargetNodeId(),
                        edge.getEdgeType(), reason));
                log.warn("隔离无效图关系: type={}, source={}, target={}, reason={}",
                        edge.getEdgeType(), edge.getSourceNodeId(), edge.getTargetNodeId(), reason);
                continue;
            }
            accepted.add(edge);
            identities.add(identity(edge));
            if (PREREQUISITE.equals(edge.getEdgeType())) {
                prerequisites.computeIfAbsent(edge.getSourceNodeId(), ignored -> new HashSet<>())
                        .add(edge.getTargetNodeId());
            }
        }
        return new ValidationResult(List.copyOf(accepted), List.copyOf(rejected));
    }

    private String invalidReason(GraphEdge edge, Set<String> validNodeIds, Set<String> identities,
                                 Map<String, Set<String>> prerequisites) {
        if (edge == null || edge.getSourceNodeId() == null || edge.getTargetNodeId() == null
                || edge.getEdgeType() == null) return "MISSING_REQUIRED_FIELD";
        if (!validNodeIds.contains(edge.getSourceNodeId()) || !validNodeIds.contains(edge.getTargetNodeId()))
            return "INVALID_ENDPOINT";
        if (edge.getSourceNodeId().equals(edge.getTargetNodeId())) return "SELF_LOOP";
        if (edge.getWeight() != null && (edge.getWeight() < 0 || edge.getWeight() > 1))
            return "WEIGHT_OUT_OF_RANGE";
        if (identities.contains(identity(edge))) return "DUPLICATE_EDGE";
        if (PREREQUISITE.equals(edge.getEdgeType())
                && reachable(edge.getTargetNodeId(), edge.getSourceNodeId(), prerequisites))
            return "PREREQUISITE_CYCLE";
        return null;
    }

    private boolean reachable(String start, String target, Map<String, Set<String>> graph) {
        ArrayDeque<String> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!visited.add(current)) continue;
            if (current.equals(target)) return true;
            pending.addAll(graph.getOrDefault(current, Set.of()));
        }
        return false;
    }

    private String identity(GraphEdge edge) {
        return edge.getSourceNodeId() + "\u0000" + edge.getEdgeType() + "\u0000" + edge.getTargetNodeId();
    }

    public record ValidationResult(List<GraphEdge> accepted, List<RejectedEdge> rejected) {}
    public record RejectedEdge(String sourceNodeId, String targetNodeId, String edgeType, String reason) {}
}
