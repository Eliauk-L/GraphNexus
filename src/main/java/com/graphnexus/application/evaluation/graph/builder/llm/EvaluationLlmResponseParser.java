package com.graphnexus.application.evaluation.graph.builder.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.application.evaluation.graph.model.CandidateDependency;
import com.graphnexus.application.evaluation.graph.model.CandidateGraph;
import com.graphnexus.application.evaluation.graph.model.CandidateTopic;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EvaluationLlmResponseParser {

    private final ObjectMapper objectMapper;

    public ParseResult parse(String response) {
        if (response == null || response.isBlank()) throw invalid("LLM 返回空响应");
        try {
            String json = extractJson(response);
            JsonNode root = objectMapper.readTree(json);
            JsonNode points = root.path("knowledgePoints");
            JsonNode relations = root.path("prerequisites");
            if (!points.isArray() || !relations.isArray()) throw invalid("LLM 响应缺少 knowledgePoints/prerequisites 数组");
            List<CandidateTopic> topics = new ArrayList<>();
            Map<String, String> idByName = new HashMap<>();
            for (int i = 0; i < points.size(); i++) {
                JsonNode node = points.get(i);
                String name = text(node, "name");
                if (name.isBlank()) continue;
                String id = first(node, "tempId", "id");
                if (id.isBlank()) id = "kp-" + i;
                topics.add(new CandidateTopic(id, name, nullable(node, "type"), nullable(node, "domain"), nullable(node, "description")));
                idByName.putIfAbsent(name.trim(), id);
            }
            List<String> warnings = new ArrayList<>();
            List<CandidateDependency> dependencies = new ArrayList<>();
            for (JsonNode node : relations) {
                String source = resolve(node, topics, idByName, true);
                String target = resolve(node, topics, idByName, false);
                if (source == null || target == null) {
                    warnings.add("已过滤端点无法解析的 LLM 依赖");
                    continue;
                }
                dependencies.add(new CandidateDependency(source, target, nullable(node, "strength")));
            }
            return new ParseResult(new CandidateGraph(topics, dependencies), warnings);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalid("LLM 返回非合法评测 JSON: " + ex.getMessage());
        }
    }

    private String resolve(JsonNode node, List<CandidateTopic> topics, Map<String, String> idByName, boolean source) {
        String direct = source ? first(node, "prerequisiteTempId", "sourceTempId", "sourceId")
                : first(node, "topicTempId", "targetTempId", "targetId");
        if (!direct.isBlank() && topics.stream().anyMatch(topic -> topic.tempId().equals(direct))) return direct;
        String name = source ? first(node, "prerequisiteName", "sourceName") : first(node, "topicName", "targetName");
        if (!name.isBlank()) return idByName.get(name.trim());
        Integer index = index(node, source
                ? new String[]{"prerequisiteIndex", "sourceKnowledgePointIndex", "sourceIndex"}
                : new String[]{"topicIndex", "targetKnowledgePointIndex", "targetIndex"});
        return index != null && index >= 0 && index < topics.size() ? topics.get(index).tempId() : null;
    }

    private Integer index(JsonNode node, String[] fields) {
        for (String field : fields) if (node.has(field) && node.get(field).canConvertToInt()) return node.get(field).asInt();
        return null;
    }

    private String extractJson(String raw) {
        String cleaned = raw.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end < start) throw invalid("LLM 响应中未找到 JSON 对象");
        return cleaned.substring(start, end + 1);
    }

    private String first(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private String text(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText().trim() : "";
    }

    private String nullable(JsonNode node, String field) {
        String value = text(node, field);
        return value.isBlank() ? null : value;
    }

    private BusinessException invalid(String message) { return new BusinessException(ErrorCode.A0010, message); }

    public record ParseResult(CandidateGraph graph, List<String> warnings) {
        public ParseResult { warnings = List.copyOf(warnings); }
    }
}
