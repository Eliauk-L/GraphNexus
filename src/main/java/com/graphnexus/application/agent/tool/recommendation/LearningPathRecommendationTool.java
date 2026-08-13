package com.graphnexus.application.agent.tool.recommendation;

import com.graphnexus.application.agent.tool.EvidenceRef;
import com.graphnexus.application.agent.tool.TeachingTool;
import com.graphnexus.application.agent.tool.ToolExecutionContext;
import com.graphnexus.application.agent.tool.ToolResult;
import com.graphnexus.application.agent.tool.weakness.WeaknessAnalysisInput;
import com.graphnexus.application.agent.tool.weakness.WeaknessAnalysisResult;
import com.graphnexus.application.agent.tool.weakness.WeaknessAnalysisTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class LearningPathRecommendationTool
        implements TeachingTool<LearningPathInput, LearningPathResult> {
    public static final String NAME = "learning_path_recommendation";
    private final WeaknessAnalysisTool weaknessTool;

    @Override public String name() { return NAME; }
    @Override public String description() { return "依据薄弱点及前置依赖生成满足拓扑顺序的个性化学习计划"; }
    @Override public Class<LearningPathInput> inputType() { return LearningPathInput.class; }

    @Override
    public ToolResult<LearningPathResult> execute(LearningPathInput input, ToolExecutionContext context) {
        long start = System.currentTimeMillis();
        if (input == null || blank(input.studentNo()) || blank(input.subject())) {
            return ToolResult.failure("INVALID_ARGUMENT", "studentNo 和 subject 不能为空",
                    System.currentTimeMillis() - start);
        }
        var weakness = weaknessTool.execute(new WeaknessAnalysisInput(
                input.studentNo(), input.subject(), null, 3, 20), context);
        if (!weakness.success()) {
            return ToolResult.failure(weakness.errorCode(),
                    weakness.warnings().isEmpty() ? "薄弱点分析失败" : weakness.warnings().get(0),
                    System.currentTimeMillis() - start);
        }
        WeaknessAnalysisResult analysis = weakness.data();
        Map<String, NodeInfo> nodes = collectNodes(analysis);
        Set<String> selected = selectRelevantNodes(input.targetKnowledgePointIds(), analysis, nodes);
        List<String> ordered = topologicalOrder(selected, analysis);
        if (ordered.size() != selected.size()) {
            return ToolResult.failure("PREREQUISITE_CYCLE",
                    "知识依赖图存在环，无法生成可靠学习顺序", System.currentTimeMillis() - start);
        }
        int dailyMinutes = input.dailyMinutes() == null ? 45 : Math.max(10, input.dailyMinutes());
        int days = input.days() == null ? 7 : Math.max(1, input.days());
        int budget = dailyMinutes * days;
        List<LearningPathResult.LearningStep> steps = allocate(ordered, nodes, dailyMinutes, budget);
        String goal = targetNames(input.targetKnowledgePointIds(), analysis, nodes);
        LearningPathResult result = new LearningPathResult(goal,
                steps.stream().mapToInt(LearningPathResult.LearningStep::plannedMinutes).sum(), steps);
        List<EvidenceRef> evidence = analysis.prerequisiteEdges().stream()
                .map(edge -> new EvidenceRef("PREREQUISITE", edge.sourceNodeId() + "->" + edge.targetNodeId(),
                        "前置依赖强度=" + edge.weight())).toList();
        return ToolResult.success(result, evidence, System.currentTimeMillis() - start, steps.size());
    }

    private Map<String, NodeInfo> collectNodes(WeaknessAnalysisResult analysis) {
        Map<String, NodeInfo> nodes = new LinkedHashMap<>();
        analysis.rootCauses().forEach(root -> nodes.put(root.knowledgePointId(),
                new NodeInfo(root.name(), root.mastery(), false)));
        analysis.weakPoints().forEach(weak -> nodes.put(weak.knowledgePointId(),
                new NodeInfo(weak.name(), weak.mastery(), true)));
        analysis.prerequisiteEdges().forEach(edge -> {
            nodes.putIfAbsent(edge.sourceNodeId(), new NodeInfo(edge.sourceNodeId(), null, false));
            nodes.putIfAbsent(edge.targetNodeId(), new NodeInfo(edge.targetNodeId(), null, false));
        });
        return nodes;
    }

    private Set<String> selectRelevantNodes(List<String> targets, WeaknessAnalysisResult analysis,
                                            Map<String, NodeInfo> nodes) {
        Set<String> selected = new LinkedHashSet<>();
        if (targets == null || targets.isEmpty()) {
            selected.addAll(nodes.keySet());
            return selected;
        }
        Set<String> requested = new HashSet<>(targets);
        selected.addAll(requested);
        boolean changed;
        do {
            changed = false;
            for (var edge : analysis.prerequisiteEdges()) {
                if (selected.contains(edge.targetNodeId()) && selected.add(edge.sourceNodeId())) changed = true;
            }
        } while (changed);
        selected.retainAll(nodes.keySet());
        return selected;
    }

    private List<String> topologicalOrder(Set<String> selected, WeaknessAnalysisResult analysis) {
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> outgoing = new HashMap<>();
        selected.forEach(id -> indegree.put(id, 0));
        analysis.prerequisiteEdges().forEach(edge -> {
            if (selected.contains(edge.sourceNodeId()) && selected.contains(edge.targetNodeId())) {
                outgoing.computeIfAbsent(edge.sourceNodeId(), ignored -> new ArrayList<>())
                        .add(edge.targetNodeId());
                indegree.merge(edge.targetNodeId(), 1, Integer::sum);
            }
        });
        outgoing.values().forEach(list -> list.sort(String::compareTo));
        PriorityQueue<String> ready = new PriorityQueue<>();
        indegree.forEach((id, degree) -> { if (degree == 0) ready.add(id); });
        List<String> ordered = new ArrayList<>();
        while (!ready.isEmpty()) {
            String current = ready.remove();
            ordered.add(current);
            for (String next : outgoing.getOrDefault(current, List.of())) {
                if (indegree.compute(next, (ignored, value) -> value - 1) == 0) ready.add(next);
            }
        }
        return ordered;
    }

    private List<LearningPathResult.LearningStep> allocate(
            List<String> ordered, Map<String, NodeInfo> nodes, int dailyMinutes, int budget) {
        if (ordered.isEmpty()) return List.of();
        int baseMinutes = Math.max(10, budget / ordered.size());
        int spent = 0;
        List<LearningPathResult.LearningStep> steps = new ArrayList<>();
        for (int index = 0; index < ordered.size() && spent < budget; index++) {
            String id = ordered.get(index);
            NodeInfo node = nodes.get(id);
            int minutes = Math.min(baseMinutes, budget - spent);
            int day = Math.min(spent / dailyMinutes + 1, Math.max(1, budget / dailyMinutes));
            steps.add(new LearningPathResult.LearningStep(index + 1, day, id, node.name,
                    node.target ? "当前薄弱目标" : "目标知识的前置基础",
                    node.mastery, minutes, "相关练习正确率达到80%"));
            spent += minutes;
        }
        return steps;
    }

    private String targetNames(List<String> targets, WeaknessAnalysisResult analysis,
                               Map<String, NodeInfo> nodes) {
        List<String> ids = targets == null || targets.isEmpty()
                ? analysis.weakPoints().stream().map(WeaknessAnalysisResult.WeakPoint::knowledgePointId).toList()
                : targets;
        List<String> names = ids.stream().filter(nodes::containsKey).map(id -> nodes.get(id).name)
                .sorted(Comparator.naturalOrder()).toList();
        return names.isEmpty() ? "巩固当前薄弱知识点" : "掌握" + String.join("、", names);
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private record NodeInfo(String name, Double mastery, boolean target) {}
}
