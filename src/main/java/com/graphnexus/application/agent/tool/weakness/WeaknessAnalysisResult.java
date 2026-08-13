package com.graphnexus.application.agent.tool.weakness;

import com.graphnexus.application.graph.construction.model.GraphEdgeData;

import java.util.List;

public record WeaknessAnalysisResult(
        List<WeakPoint> weakPoints,
        List<RootCause> rootCauses,
        List<GraphEdgeData> prerequisiteEdges,
        boolean masteryAvailable
) {
    public record WeakPoint(String knowledgePointId, String name, double mastery, double priority) {}
    public record RootCause(String knowledgePointId, String name, Double mastery, int downstreamCount) {}
}
