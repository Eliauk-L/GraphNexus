package com.graphnexus.application.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.application.agent.model.AgentAction;
import com.graphnexus.application.agent.model.AgentRequest;
import com.graphnexus.application.agent.model.AgentToolCall;
import com.graphnexus.application.agent.tool.TeachingTool;
import com.graphnexus.common.LlmGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/** 使用严格 JSON 动作协议的 LLM Planner，不解析自由文本 Thought/Action。 */
@Component
@RequiredArgsConstructor
public class LlmAgentPlanner implements AgentPlanner {
    private final LlmGateway llmGateway;
    private final ObjectMapper objectMapper;

    @Override
    public AgentAction nextAction(AgentRequest request, List<AgentToolCall> history,
                                  Collection<TeachingTool<?, ?>> availableTools) {
        String tools = availableTools.stream()
                .map(tool -> tool.name() + ": " + tool.description())
                .collect(java.util.stream.Collectors.joining("\n"));
        String system = """
                你是教学辅助 Agent 的动作规划器。每轮只能输出一个 JSON 对象，不得输出 markdown。
                type 只能是 TOOL_CALL 或 FINAL_ANSWER。
                TOOL_CALL 格式：{"type":"TOOL_CALL","tool":"工具名","arguments":{},"decisionSummary":"公开的决策摘要"}
                FINAL_ANSWER 格式：{"type":"FINAL_ANSWER","answerPlan":"回答提纲"}
                不得调用未提供的工具，不得生成 SQL 或 Cypher。
                可用工具：
                """ + tools;
        String user = writeJson(java.util.Map.of(
                "question", request.question(),
                "studentNo", request.studentNo(),
                "subject", request.subject(),
                "history", compactHistory(history)));
        try {
            return objectMapper.readValue(stripFence(llmGateway.chat(system, user)), AgentAction.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Agent 动作 JSON 解析失败", exception);
        }
    }

    @Override
    public String finalAnswer(AgentRequest request, List<AgentToolCall> history, String answerPlan) {
        String system = "你是教学辅助老师。仅根据工具观察生成简洁回答；数据不足时明确说明，禁止编造数值或知识点。";
        return llmGateway.chat(system, writeJson(java.util.Map.of(
                "question", request.question(), "answerPlan", answerPlan == null ? "" : answerPlan,
                "observations", compactHistory(history))));
    }

    private List<Object> compactHistory(List<AgentToolCall> history) {
        return history.stream().map(call -> (Object) java.util.Map.of(
                "tool", call.toolName(), "success", call.observation().success(),
                "data", call.observation().data() == null ? "" : call.observation().data(),
                "warnings", call.observation().warnings())).toList();
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Agent 上下文序列化失败", exception); }
    }

    private String stripFence(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }
}
