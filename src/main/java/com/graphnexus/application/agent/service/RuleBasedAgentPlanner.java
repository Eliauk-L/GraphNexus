package com.graphnexus.application.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphnexus.application.agent.model.AgentAction;
import com.graphnexus.application.agent.model.AgentRequest;
import com.graphnexus.application.agent.model.AgentToolCall;
import com.graphnexus.application.agent.tool.TeachingTool;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** LLM Planner 不可用时的确定性工具路由。 */
@Component
public class RuleBasedAgentPlanner implements AgentPlanner {
    private final ObjectMapper objectMapper;

    public RuleBasedAgentPlanner(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentAction nextAction(AgentRequest request, List<AgentToolCall> history,
                                  Collection<TeachingTool<?, ?>> availableTools) {
        Set<String> used = history.stream().map(AgentToolCall::toolName).collect(java.util.stream.Collectors.toSet());
        String question = request.question() == null ? "" : request.question();
        if ((question.contains("什么") || question.contains("解释") || question.contains("知识点"))
                && !used.contains("knowledge_graph_search")) {
            ObjectNode args = objectMapper.createObjectNode().put("query", question)
                    .put("subject", request.subject()).put("topK", 5).put("maxHops", 2);
            return call("knowledge_graph_search", args, "定位问题涉及的知识点");
        }
        if (!used.contains("student_profile")) {
            ObjectNode args = objectMapper.createObjectNode().put("studentNo", request.studentNo())
                    .put("subject", request.subject()).put("recentExamLimit", 5);
            return call("student_profile", args, "获取学生当前画像");
        }
        if (!used.contains("weakness_analysis")) {
            ObjectNode args = objectMapper.createObjectNode().put("studentNo", request.studentNo())
                    .put("subject", request.subject()).put("weakThreshold", 0.6)
                    .put("maxHops", 2).put("topK", 10);
            return call("weakness_analysis", args, "分析薄弱点及其前置根因");
        }
        if ((question.contains("计划") || question.contains("路径") || question.contains("复习"))
                && !used.contains("learning_path_recommendation")) {
            ObjectNode args = objectMapper.createObjectNode().put("studentNo", request.studentNo())
                    .put("subject", request.subject())
                    .put("dailyMinutes", request.dailyMinutes() == null ? 45 : request.dailyMinutes())
                    .put("days", request.days() == null ? 7 : request.days());
            args.putArray("targetKnowledgePointIds");
            return call("learning_path_recommendation", args, "生成满足前置顺序的学习计划");
        }
        return new AgentAction(AgentAction.ActionType.FINAL_ANSWER, null, null,
                "已有足够证据", "汇总工具证据并给出建议");
    }

    @Override
    public String finalAnswer(AgentRequest request, List<AgentToolCall> history, String answerPlan) {
        return "已完成教学分析，共调用 " + history.size() + " 个工具；请结合结构化证据查看薄弱点与学习路径。";
    }

    private AgentAction call(String tool, ObjectNode arguments, String summary) {
        return new AgentAction(AgentAction.ActionType.TOOL_CALL, tool, arguments, summary, null);
    }
}
