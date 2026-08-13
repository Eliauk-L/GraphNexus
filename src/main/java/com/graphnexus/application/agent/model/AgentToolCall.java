package com.graphnexus.application.agent.model;

import com.graphnexus.application.agent.tool.ToolResult;

public record AgentToolCall(
        int round,
        String toolName,
        String argumentsJson,
        String decisionSummary,
        ToolResult<?> observation
) {}
