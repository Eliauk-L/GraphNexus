package com.graphnexus.application.agent.tool.weakness;

public record WeaknessAnalysisInput(
        String studentNo, String subject, Double weakThreshold, Integer maxHops, Integer topK) {}
