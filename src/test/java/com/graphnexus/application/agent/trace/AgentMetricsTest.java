package com.graphnexus.application.agent.trace;

import com.graphnexus.application.agent.model.AgentResponse;
import com.graphnexus.application.agent.model.AgentToolCall;
import com.graphnexus.application.agent.tool.ToolResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentMetricsTest {
    @Test
    void recordsTaskFallbackAndToolDimensions() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentMetrics metrics = new AgentMetrics(registry);
        AgentToolCall call = new AgentToolCall(1, "student_profile", "{}", "read",
                ToolResult.success("ok", List.of(), 12, 1));
        AgentResponse response = new AgentResponse("task", "PARTIAL", "answer",
                List.of("student_profile"), List.of(), List.of(call), List.of(), "LLM_PLANNER_FAILED");

        metrics.record(response, 25);

        assertEquals(1, registry.get("graphnexus.agent.tasks").tag("status", "PARTIAL").counter().count());
        assertEquals(1, registry.get("graphnexus.agent.fallbacks").counter().count());
        assertEquals(1, registry.get("graphnexus.agent.tool.calls").tag("outcome", "success").counter().count());
    }
}
