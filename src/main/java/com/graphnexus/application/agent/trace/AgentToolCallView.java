package com.graphnexus.application.agent.trace;

import java.time.LocalDateTime;

public record AgentToolCallView(
        Long id,
        String taskId,
        Integer roundNo,
        String toolName,
        String argumentsJson,
        String observationJson,
        String decisionSummary,
        String status,
        Long elapsedMs,
        String errorMessage,
        LocalDateTime createTime
) {}
