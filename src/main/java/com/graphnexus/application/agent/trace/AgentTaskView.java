package com.graphnexus.application.agent.trace;

import java.time.LocalDateTime;

public record AgentTaskView(
        String taskId,
        String userId,
        String status,
        String answerText,
        String fallbackReason,
        LocalDateTime createTime
) {}
