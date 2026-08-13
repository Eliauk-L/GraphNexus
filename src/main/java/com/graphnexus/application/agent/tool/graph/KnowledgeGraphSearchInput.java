package com.graphnexus.application.agent.tool.graph;

public record KnowledgeGraphSearchInput(String query, String subject, Integer topK, Integer maxHops) {}
