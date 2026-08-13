package com.graphnexus.application.agent.tool.recommendation;

import java.util.List;

public record LearningPathInput(
        String studentNo,
        String subject,
        List<String> targetKnowledgePointIds,
        Integer dailyMinutes,
        Integer days
) {
    public LearningPathInput {
        targetKnowledgePointIds = targetKnowledgePointIds == null
                ? List.of() : List.copyOf(targetKnowledgePointIds);
    }
}
