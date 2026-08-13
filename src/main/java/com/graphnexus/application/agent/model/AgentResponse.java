package com.graphnexus.application.agent.model;

import com.graphnexus.application.agent.tool.EvidenceRef;

import java.util.List;

public record AgentResponse(
        String taskId,
        String status,
        String answer,
        List<String> toolsUsed,
        List<EvidenceRef> evidence,
        List<AgentToolCall> trace,
        List<String> warnings,
        String fallbackReason
) {}
