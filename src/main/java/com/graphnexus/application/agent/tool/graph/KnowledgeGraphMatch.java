package com.graphnexus.application.agent.tool.graph;

import java.util.List;

public record KnowledgeGraphMatch(
        String knowledgePointId,
        String name,
        String description,
        String documentId,
        double score,
        List<String> prerequisites,
        List<String> dependents
) {}
