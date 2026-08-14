package com.graphnexus.application.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.application.agent.config.AgentProperties;
import com.graphnexus.application.agent.model.AgentAction;
import com.graphnexus.application.agent.tool.TeachingTool;
import com.graphnexus.application.agent.tool.TeachingToolRegistry;
import com.graphnexus.application.agent.tool.ToolExecutionContext;
import com.graphnexus.application.agent.tool.ToolMetrics;
import com.graphnexus.application.agent.tool.ToolResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class AgentToolExecutor {
    private final TeachingToolRegistry registry;
    private final ObjectMapper objectMapper;
    private final AgentProperties properties;
    private final ExecutorService executor;

    public AgentToolExecutor(TeachingToolRegistry registry, ObjectMapper objectMapper,
                             AgentProperties properties,
                             @Qualifier("agentToolExecutor") ExecutorService executor) {
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.executor = executor;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public ToolResult<?> execute(AgentAction action, ToolExecutionContext context) {
        TeachingTool tool = registry.find(action.tool()).orElse(null);
        if (tool == null) return ToolResult.failure("UNKNOWN_TOOL", "未知 Tool: " + action.tool(), 0);
        long startedAt = System.currentTimeMillis();
        Future<ToolResult<?>> future = executor.submit(() -> {
            Object input = objectMapper.treeToValue(action.arguments(), tool.inputType());
            return tool.execute(input, context);
        });
        try {
            long remainingMs = Math.max(1, Duration.between(Instant.now(), context.deadline()).toMillis());
            long timeoutMs = Math.max(1, Math.min(properties.getToolTimeoutMs(), remainingMs));
            return limitObservation(future.get(timeoutMs, TimeUnit.MILLISECONDS));
        } catch (TimeoutException exception) {
            future.cancel(true);
            return ToolResult.failure("TOOL_TIMEOUT", "Tool 执行超时: " + action.tool(),
                    System.currentTimeMillis() - startedAt);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return ToolResult.failure("TOOL_INTERRUPTED", "Tool 执行被中断: " + action.tool(),
                    System.currentTimeMillis() - startedAt);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            return ToolResult.failure("TOOL_EXECUTION_FAILED", safeMessage(cause),
                    System.currentTimeMillis() - startedAt);
        }
    }

    private ToolResult<?> limitObservation(ToolResult<?> result) {
        if (result == null) return ToolResult.failure("EMPTY_OBSERVATION", "Tool 未返回结果", 0);
        try {
            String json = objectMapper.writeValueAsString(result.data());
            if (json.length() <= properties.getMaxObservationChars()) return result;
            int limit = Math.max(0, properties.getMaxObservationChars());
            ArrayList<String> warnings = new ArrayList<>(result.warnings());
            warnings.add("Tool Observation 超出字符上限，已截断");
            return new ToolResult<>(result.success(), Map.of(
                    "truncatedJson", json.substring(0, Math.min(limit, json.length())),
                    "originalChars", json.length()), result.evidence(), warnings,
                    new ToolMetrics(result.metrics().elapsedMs(), result.metrics().resultCount(), true),
                    result.errorCode());
        } catch (Exception exception) {
            return ToolResult.failure("OBSERVATION_SERIALIZATION_FAILED", safeMessage(exception),
                    result.metrics().elapsedMs());
        }
    }

    private String safeMessage(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }
}
