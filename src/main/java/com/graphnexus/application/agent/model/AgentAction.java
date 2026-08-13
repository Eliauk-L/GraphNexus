package com.graphnexus.application.agent.model;

import com.fasterxml.jackson.databind.JsonNode;

public record AgentAction(
        ActionType type,
        String tool,
        JsonNode arguments,
        String decisionSummary,
        String answerPlan
) {
    public enum ActionType { TOOL_CALL, FINAL_ANSWER }
}
