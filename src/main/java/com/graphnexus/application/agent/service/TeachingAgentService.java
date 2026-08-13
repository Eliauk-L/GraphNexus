package com.graphnexus.application.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.application.agent.config.AgentProperties;
import com.graphnexus.application.agent.model.AgentAction;
import com.graphnexus.application.agent.model.AgentRequest;
import com.graphnexus.application.agent.model.AgentResponse;
import com.graphnexus.application.agent.model.AgentToolCall;
import com.graphnexus.application.agent.tool.EvidenceRef;
import com.graphnexus.application.agent.tool.TeachingTool;
import com.graphnexus.application.agent.tool.TeachingToolRegistry;
import com.graphnexus.application.agent.tool.ToolExecutionContext;
import com.graphnexus.application.agent.tool.ToolResult;
import com.graphnexus.application.agent.trace.AgentTraceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeachingAgentService {
    private final TeachingToolRegistry registry;
    private final LlmAgentPlanner llmPlanner;
    private final RuleBasedAgentPlanner fallbackPlanner;
    private final ObjectMapper objectMapper;
    private final AgentProperties properties;
    private final AgentTraceService traceService;

    public AgentResponse execute(AgentRequest request, ToolExecutionContext suppliedContext) {
        String taskId = suppliedContext.taskId() == null ? UUID.randomUUID().toString() : suppliedContext.taskId();
        Instant deadline = Instant.now().plusMillis(properties.getTotalTimeoutMs());
        ToolExecutionContext context = new ToolExecutionContext(taskId, suppliedContext.userId(),
                suppliedContext.roles(), suppliedContext.allowedStudentNos(), deadline, suppliedContext.traceId());
        List<AgentToolCall> history = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> signatures = new LinkedHashSet<>();
        int consecutiveErrors = 0;
        String fallbackReason = null;
        AgentPlanner planner = llmPlanner;

        for (int round = 1; round <= properties.getMaxRounds(); round++) {
            if (Instant.now().isAfter(deadline)) {
                warnings.add("Agent 总执行时间已达上限");
                break;
            }
            AgentAction action;
            try {
                action = planner.nextAction(request, history, registry.all());
                validate(action);
            } catch (Exception exception) {
                if (planner == fallbackPlanner) {
                    warnings.add("规则规划失败: " + exception.getMessage());
                    break;
                }
                planner = fallbackPlanner;
                fallbackReason = "LLM_PLANNER_FAILED";
                action = planner.nextAction(request, history, registry.all());
            }
            if (action.type() == AgentAction.ActionType.FINAL_ANSWER) {
                String answer = planner.finalAnswer(request, history, action.answerPlan());
                AgentResponse response = response(taskId, "COMPLETED", answer, history, warnings, fallbackReason);
                traceService.save(response, context.userId());
                return response;
            }
            String signature = action.tool() + ":" + action.arguments();
            if (!signatures.add(signature)) {
                warnings.add("阻止重复 Tool 调用: " + action.tool());
                consecutiveErrors++;
                if (consecutiveErrors >= properties.getMaxConsecutiveErrors()) break;
                continue;
            }
            ToolResult<?> observation = executeTool(action, context);
            history.add(new AgentToolCall(round, action.tool(), action.arguments().toString(),
                    action.decisionSummary(), observation));
            consecutiveErrors = observation.success() ? 0 : consecutiveErrors + 1;
            if (consecutiveErrors >= properties.getMaxConsecutiveErrors()) {
                warnings.add("连续 Tool 失败次数达到上限");
                break;
            }
        }
        String answer = fallbackPlanner.finalAnswer(request, history, "根据已有证据生成不完整回答");
        AgentResponse response = response(taskId, "PARTIAL", answer, history, warnings,
                fallbackReason == null ? "MAX_ROUNDS_OR_TIMEOUT" : fallbackReason);
        traceService.save(response, context.userId());
        return response;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ToolResult<?> executeTool(AgentAction action, ToolExecutionContext context) {
        TeachingTool tool = registry.find(action.tool()).orElse(null);
        if (tool == null) return ToolResult.failure("UNKNOWN_TOOL", "未知 Tool: " + action.tool(), 0);
        try {
            Object input = objectMapper.treeToValue(action.arguments(), tool.inputType());
            return tool.execute(input, context);
        } catch (Exception exception) {
            return ToolResult.failure("TOOL_EXECUTION_FAILED", exception.getMessage(), 0);
        }
    }

    private void validate(AgentAction action) {
        if (action == null || action.type() == null) throw new IllegalArgumentException("Agent action.type 不能为空");
        if (action.type() == AgentAction.ActionType.TOOL_CALL
                && (action.tool() == null || action.arguments() == null)) {
            throw new IllegalArgumentException("TOOL_CALL 缺少 tool 或 arguments");
        }
    }

    private AgentResponse response(String taskId, String status, String answer,
                                   List<AgentToolCall> history, List<String> warnings,
                                   String fallbackReason) {
        List<String> tools = history.stream().map(AgentToolCall::toolName).distinct().toList();
        List<EvidenceRef> evidence = history.stream().flatMap(call -> call.observation().evidence().stream()).toList();
        return new AgentResponse(taskId, status, answer, tools, evidence,
                List.copyOf(history), List.copyOf(warnings), fallbackReason);
    }
}
