package com.graphnexus.application.agent.tool.recommendation;

import java.util.List;

public record LearningPathResult(
        String goal,
        int totalMinutes,
        List<LearningStep> steps
) {
    public record LearningStep(
            int order,
            int day,
            String knowledgePointId,
            String name,
            String reason,
            Double currentMastery,
            int plannedMinutes,
            String successCriterion
    ) {}
}
