package com.graphnexus.application.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.application.agent.model.AgentAction;
import com.graphnexus.application.agent.model.AgentRequest;
import com.graphnexus.common.LlmGateway;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmAgentPlannerTest {
    @Test
    void parsesStrictJsonAction() {
        LlmGateway gateway = mock(LlmGateway.class);
        when(gateway.chat(anyString(), anyString())).thenReturn(
                "{\"type\":\"FINAL_ANSWER\",\"answerPlan\":\"总结\"}");
        var action = new LlmAgentPlanner(gateway, new ObjectMapper()).nextAction(
                new AgentRequest("问题", "S001", "数学", null, null), List.of(), List.of());
        assertEquals(AgentAction.ActionType.FINAL_ANSWER, action.type());
    }

    @Test
    void rejectsNonJsonPlannerOutput() {
        LlmGateway gateway = mock(LlmGateway.class);
        when(gateway.chat(anyString(), anyString())).thenReturn("Thought: call a tool");
        assertThrows(IllegalStateException.class, () ->
                new LlmAgentPlanner(gateway, new ObjectMapper()).nextAction(
                        new AgentRequest("问题", "S001", "数学", null, null), List.of(), List.of()));
    }
}
