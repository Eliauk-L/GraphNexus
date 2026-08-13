package com.graphnexus.application.agent.tool.weakness;

import com.graphnexus.application.agent.tool.EvidenceRef;
import com.graphnexus.application.agent.tool.TeachingTool;
import com.graphnexus.application.agent.tool.ToolExecutionContext;
import com.graphnexus.application.agent.tool.ToolResult;
import com.graphnexus.application.analysis.model.PruningRequest;
import com.graphnexus.application.analysis.strategy.StudentDiagnosisStrategy;
import com.graphnexus.application.graph.construction.model.GraphNodeData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class WeaknessAnalysisTool
        implements TeachingTool<WeaknessAnalysisInput, WeaknessAnalysisResult> {
    public static final String NAME = "weakness_analysis";
    private final StudentDiagnosisStrategy diagnosisStrategy;

    @Override public String name() { return NAME; }
    @Override public String description() { return "根据掌握度剪枝并沿前置依赖诊断学生薄弱根因"; }
    @Override public Class<WeaknessAnalysisInput> inputType() { return WeaknessAnalysisInput.class; }

    @Override
    public ToolResult<WeaknessAnalysisResult> execute(
            WeaknessAnalysisInput input, ToolExecutionContext context) {
        long start = System.currentTimeMillis();
        if (input == null || blank(input.studentNo()) || blank(input.subject())) {
            return ToolResult.failure("INVALID_ARGUMENT", "studentNo 和 subject 不能为空",
                    System.currentTimeMillis() - start);
        }
        if (!context.canAccessStudent(input.studentNo())) {
            return ToolResult.failure("STUDENT_SCOPE_DENIED", "无权分析该学生",
                    System.currentTimeMillis() - start);
        }
        double threshold = input.weakThreshold() == null ? 0.6 : input.weakThreshold();
        int maxHops = input.maxHops() == null ? 2 : input.maxHops();
        int topK = input.topK() == null ? 10 : input.topK();
        var graph = diagnosisStrategy.prune(new PruningRequest(
                "STUDENT_DIAGNOSIS", input.studentNo(), input.subject(),
                Map.of("weakThreshold", threshold, "maxHops", (double) maxHops,
                        "topK", (double) topK)));

        Map<String, GraphNodeData> nodes = graph.nodes().stream()
                .collect(Collectors.toMap(GraphNodeData::id, node -> node, (a, b) -> a));
        Set<String> weakIds = graph.edges().stream()
                .filter(edge -> "MASTERS".equals(edge.edgeType())
                        && edge.weight() != null && edge.weight() < threshold)
                .map(edge -> edge.targetNodeId()).collect(Collectors.toSet());
        List<WeaknessAnalysisResult.WeakPoint> weakPoints = graph.edges().stream()
                .filter(edge -> "MASTERS".equals(edge.edgeType()) && weakIds.contains(edge.targetNodeId()))
                .map(edge -> new WeaknessAnalysisResult.WeakPoint(
                        edge.targetNodeId(), name(nodes.get(edge.targetNodeId())), edge.weight(),
                        1 - edge.weight()))
                .sorted(Comparator.comparingDouble(WeaknessAnalysisResult.WeakPoint::priority).reversed()
                        .thenComparing(WeaknessAnalysisResult.WeakPoint::knowledgePointId)).toList();

        Map<String, Integer> downstream = new HashMap<>();
        graph.edges().stream().filter(edge -> "PREREQUISITE_OF".equals(edge.edgeType()))
                .forEach(edge -> downstream.merge(edge.sourceNodeId(), 1, Integer::sum));
        List<WeaknessAnalysisResult.RootCause> roots = new ArrayList<>();
        downstream.forEach((id, count) -> {
            if (!weakIds.contains(id)) {
                GraphNodeData node = nodes.get(id);
                roots.add(new WeaknessAnalysisResult.RootCause(id, name(node), weight(node), count));
            }
        });
        roots.sort(Comparator.comparingInt(WeaknessAnalysisResult.RootCause::downstreamCount).reversed()
                .thenComparing(WeaknessAnalysisResult.RootCause::knowledgePointId));
        var prerequisiteEdges = graph.edges().stream()
                .filter(edge -> "PREREQUISITE_OF".equals(edge.edgeType())).toList();
        WeaknessAnalysisResult result = new WeaknessAnalysisResult(
                weakPoints, roots, prerequisiteEdges, graph.meta().mastersAvailable());
        List<EvidenceRef> evidence = weakPoints.stream()
                .map(item -> new EvidenceRef("MASTERY", item.knowledgePointId(),
                        item.name() + " 掌握度=" + item.mastery())).toList();
        return ToolResult.success(result, evidence, System.currentTimeMillis() - start,
                weakPoints.size() + roots.size());
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String name(GraphNodeData node) {
        if (node == null) return "未知知识点";
        Object value = node.properties().get("name");
        return value == null ? node.label() : value.toString();
    }
    private Double weight(GraphNodeData node) {
        if (node == null) return null;
        Object value = node.properties().get("weight");
        return value instanceof Number number ? number.doubleValue() : null;
    }
}
