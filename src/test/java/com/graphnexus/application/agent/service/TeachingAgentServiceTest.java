package com.graphnexus.application.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphnexus.application.agent.config.AgentProperties;
import com.graphnexus.application.agent.model.AgentAction;
import com.graphnexus.application.agent.model.AgentRequest;
import com.graphnexus.application.agent.tool.TeachingTool;
import com.graphnexus.application.agent.tool.TeachingToolRegistry;
import com.graphnexus.application.agent.tool.ToolExecutionContext;
import com.graphnexus.application.agent.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TeachingAgentServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void executesStructuredToolCallThenFinalAnswer() {
        QueuePlanner planner = new QueuePlanner(
                call("echo", "第一次查询"),
                new AgentAction(AgentAction.ActionType.FINAL_ANSWER, null, null, null, "总结"));
        TeachingAgentService service = service(planner, new RuleBasedAgentPlanner(objectMapper), 6, tool());

        var result = service.execute(new AgentRequest("分析学习情况", "S001", "数学", null, null), context());

        assertEquals("COMPLETED", result.status());
        assertEquals(List.of("echo"), result.toolsUsed());
        assertEquals(1, result.trace().size());
        assertEquals("final", result.answer());
    }

    @Test
    void blocksDuplicateCallsAndStopsAfterErrorLimit() {
        AgentAction repeated = call("echo", "重复");
        QueuePlanner planner = new QueuePlanner(repeated, repeated, repeated);
        TeachingAgentService service = service(planner, new RuleBasedAgentPlanner(objectMapper), 5, tool());

        var result = service.execute(new AgentRequest("分析", "S001", "数学", null, null), context());

        assertEquals("PARTIAL", result.status());
        assertEquals(1, result.trace().size());
        assertTrue(result.warnings().stream().anyMatch(text -> text.contains("重复 Tool")));
    }

    @Test
    void fallsBackWhenLlmPlannerFails() {
        AgentPlanner failing = new AgentPlanner() {
            public AgentAction nextAction(AgentRequest request, java.util.List<com.graphnexus.application.agent.model.AgentToolCall> history,
                                          java.util.Collection<TeachingTool<?, ?>> tools) {
                throw new IllegalStateException("bad json");
            }
            public String finalAnswer(AgentRequest request, java.util.List<com.graphnexus.application.agent.model.AgentToolCall> history, String plan) {
                return "never";
            }
        };
        TeachingAgentService service = service(failing, new RuleBasedAgentPlanner(objectMapper), 6,
                namedTool("student_profile"), namedTool("weakness_analysis"));

        var result = service.execute(new AgentRequest("分析", "S001", "数学", null, null), context());

        assertEquals("COMPLETED", result.status());
        assertEquals("LLM_PLANNER_FAILED", result.fallbackReason());
        assertEquals(List.of("student_profile", "weakness_analysis"), result.toolsUsed());
    }

    private TeachingAgentService service(AgentPlanner planner, RuleBasedAgentPlanner fallback,
                                         int maxRounds, TeachingTool<?, ?>... tools) {
        AgentProperties properties = new AgentProperties();
        properties.setMaxRounds(maxRounds);
        properties.setMaxConsecutiveErrors(2);
        properties.setTotalTimeoutMs(30000);
        return new TeachingAgentService(new TeachingToolRegistry(List.of(tools)),
                planner instanceof LlmAgentPlanner llm ? llm : proxy(planner),
                fallback, objectMapper, properties);
    }

    private LlmAgentPlanner proxy(AgentPlanner planner) {
        return new LlmAgentPlanner(mock(com.graphnexus.common.LlmGateway.class), objectMapper) {
            @Override public AgentAction nextAction(AgentRequest request,
                    java.util.List<com.graphnexus.application.agent.model.AgentToolCall> history,
                    java.util.Collection<TeachingTool<?, ?>> tools) {
                return planner.nextAction(request, history, tools);
            }
            @Override public String finalAnswer(AgentRequest request,
                    java.util.List<com.graphnexus.application.agent.model.AgentToolCall> history, String plan) {
                return planner.finalAnswer(request, history, plan);
            }
        };
    }

    private TeachingTool<com.fasterxml.jackson.databind.JsonNode, com.fasterxml.jackson.databind.JsonNode> tool() {
        return namedTool("echo");
    }
    private TeachingTool<com.fasterxml.jackson.databind.JsonNode, com.fasterxml.jackson.databind.JsonNode> namedTool(String name) {
        return new TeachingTool<>() {
            public String name() { return name; }
            public String description() { return name; }
            public Class<com.fasterxml.jackson.databind.JsonNode> inputType() {
                return com.fasterxml.jackson.databind.JsonNode.class;
            }
            public ToolResult<com.fasterxml.jackson.databind.JsonNode> execute(
                    com.fasterxml.jackson.databind.JsonNode input, ToolExecutionContext context) {
                return ToolResult.success(input, List.of(), 1, 1);
            }
        };
    }

    private AgentAction call(String tool, String argument) {
        ObjectNode args = objectMapper.createObjectNode().put("value", argument);
        return new AgentAction(AgentAction.ActionType.TOOL_CALL, tool, args, argument, null);
    }

    private ToolExecutionContext context() {
        return new ToolExecutionContext("task", "teacher", Set.of("TEACHER"),
                Set.of("S001"), Instant.MAX, "trace");
    }

    private static class QueuePlanner implements AgentPlanner {
        private final Queue<AgentAction> actions;
        QueuePlanner(AgentAction... actions) { this.actions = new ArrayDeque<>(List.of(actions)); }
        public AgentAction nextAction(AgentRequest request,
                java.util.List<com.graphnexus.application.agent.model.AgentToolCall> history,
                java.util.Collection<TeachingTool<?, ?>> tools) { return actions.remove(); }
        public String finalAnswer(AgentRequest request,
                java.util.List<com.graphnexus.application.agent.model.AgentToolCall> history, String plan) { return "final"; }
    }
}
