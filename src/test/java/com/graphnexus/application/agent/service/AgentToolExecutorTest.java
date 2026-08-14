package com.graphnexus.application.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphnexus.application.agent.config.AgentProperties;
import com.graphnexus.application.agent.model.AgentAction;
import com.graphnexus.application.agent.tool.TeachingTool;
import com.graphnexus.application.agent.tool.TeachingToolRegistry;
import com.graphnexus.application.agent.tool.ToolExecutionContext;
import com.graphnexus.application.agent.tool.ToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolExecutorTest {
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final ObjectMapper mapper = new ObjectMapper();

    @AfterEach void shutdown() { executorService.shutdownNow(); }

    @Test
    void returnsStructuredTimeout() {
        AgentProperties properties = properties(10, 100);
        AgentToolExecutor executor = executor(properties, input -> {
            try { Thread.sleep(200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            return ToolResult.success(input, List.of(), 200, 1);
        });

        var result = executor.execute(action(), context());

        assertEquals("TOOL_TIMEOUT", result.errorCode());
    }

    @Test
    void truncatesOversizedObservation() {
        AgentProperties properties = properties(1000, 20);
        AgentToolExecutor executor = executor(properties,
                input -> ToolResult.success(mapper.createObjectNode().put("content", "x".repeat(100)),
                        List.of(), 1, 1));

        var result = executor.execute(action(), context());

        assertTrue(result.metrics().truncated());
        assertTrue(result.warnings().get(0).contains("截断"));
    }

    private AgentToolExecutor executor(AgentProperties properties, ToolBody body) {
        TeachingTool<JsonNode, JsonNode> tool = new TeachingTool<>() {
            public String name() { return "test_tool"; }
            public String description() { return "test"; }
            public Class<JsonNode> inputType() { return JsonNode.class; }
            public ToolResult<JsonNode> execute(JsonNode input, ToolExecutionContext context) {
                return body.execute(input);
            }
        };
        return new AgentToolExecutor(new TeachingToolRegistry(List.of(tool)), mapper, properties, executorService);
    }

    private AgentProperties properties(long timeout, int maxChars) {
        AgentProperties properties = new AgentProperties();
        properties.setToolTimeoutMs(timeout);
        properties.setMaxObservationChars(maxChars);
        return properties;
    }

    private AgentAction action() {
        ObjectNode arguments = mapper.createObjectNode().put("value", "ok");
        return new AgentAction(AgentAction.ActionType.TOOL_CALL, "test_tool", arguments, "test", null);
    }

    private ToolExecutionContext context() {
        return new ToolExecutionContext("task", "teacher", Set.of("TEACHER"), Set.of("S001"),
                Instant.now().plusSeconds(5), "trace");
    }

    @FunctionalInterface
    private interface ToolBody { ToolResult<JsonNode> execute(JsonNode input); }
}
