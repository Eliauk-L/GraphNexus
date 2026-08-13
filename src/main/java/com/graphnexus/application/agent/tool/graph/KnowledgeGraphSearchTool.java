package com.graphnexus.application.agent.tool.graph;

import com.graphnexus.application.agent.tool.EvidenceRef;
import com.graphnexus.application.agent.tool.TeachingTool;
import com.graphnexus.application.agent.tool.ToolExecutionContext;
import com.graphnexus.application.agent.tool.ToolResult;
import com.graphnexus.infrastructure.neo4j.repository.QueryGraphRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class KnowledgeGraphSearchTool
        implements TeachingTool<KnowledgeGraphSearchInput, List<KnowledgeGraphMatch>> {
    public static final String NAME = "knowledge_graph_search";
    private final QueryGraphRepository repository;

    @Override public String name() { return NAME; }
    @Override public String description() { return "根据学生问题定位教材知识点及其一跳前后置关系"; }
    @Override public Class<KnowledgeGraphSearchInput> inputType() { return KnowledgeGraphSearchInput.class; }

    @Override
    public ToolResult<List<KnowledgeGraphMatch>> execute(
            KnowledgeGraphSearchInput input, ToolExecutionContext context) {
        long start = System.currentTimeMillis();
        if (input == null || blank(input.query()) || blank(input.subject())) {
            return ToolResult.failure("INVALID_ARGUMENT", "query 和 subject 不能为空",
                    System.currentTimeMillis() - start);
        }
        int topK = input.topK() == null ? 5 : Math.max(1, Math.min(input.topK(), 20));
        List<KnowledgeGraphMatch> matches = repository
                .searchKnowledgePoints(input.query(), input.subject(), topK).stream()
                .map(this::toMatch).toList();
        List<EvidenceRef> evidence = matches.stream()
                .map(match -> new EvidenceRef("KNOWLEDGE_POINT", match.knowledgePointId(),
                        match.name() + "，匹配分=" + match.score())).toList();
        ToolResult<List<KnowledgeGraphMatch>> result = ToolResult.success(matches, evidence,
                System.currentTimeMillis() - start, matches.size());
        if (matches.isEmpty()) {
            return new ToolResult<>(true, matches, evidence, List.of("未找到匹配知识点"),
                    result.metrics(), null);
        }
        return result;
    }

    private KnowledgeGraphMatch toMatch(Map<String, Object> row) {
        return new KnowledgeGraphMatch(
                string(row.get("kpId")), string(row.get("kpName")), string(row.get("description")),
                string(row.get("documentId")), number(row.get("score")),
                strings(row.get("prerequisites")), strings(row.get("dependents")));
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String string(Object value) { return value == null ? null : value.toString(); }
    private double number(Object value) { return value instanceof Number n ? n.doubleValue() : 0; }
    private List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(item -> item != null).map(Object::toString)
                .filter(item -> !item.isBlank()).toList();
    }
}
