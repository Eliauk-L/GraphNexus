package com.graphnexus.application.agent.service;

import com.graphnexus.application.agent.model.AgentAction;
import com.graphnexus.application.agent.model.AgentRequest;
import com.graphnexus.application.agent.model.AgentToolCall;
import com.graphnexus.application.agent.tool.TeachingTool;

import java.util.Collection;
import java.util.List;

public interface AgentPlanner {
    AgentAction nextAction(AgentRequest request, List<AgentToolCall> history,
                           Collection<TeachingTool<?, ?>> availableTools);

    String finalAnswer(AgentRequest request, List<AgentToolCall> history, String answerPlan);
}
