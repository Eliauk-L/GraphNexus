package com.graphnexus.application.evaluation.graph.gold;

import com.graphnexus.application.evaluation.graph.model.GoldDependency;
import com.graphnexus.application.evaluation.graph.model.GoldTopic;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Applies blocking invariants and non-blocking quality warnings to a gold dataset. */
@Component
public class GoldDatasetValidator {

    public List<String> validate(List<GoldTopic> topics,
                                 List<GoldDependency> dependencies,
                                 Map<String, Long> manifestTypeDistribution) {
        List<String> warnings = new ArrayList<>();
        Set<String> ids = validateTopics(topics, warnings);
        validateDependencies(ids, dependencies, warnings);
        validateDag(ids, dependencies);
        validateManifest(topics, manifestTypeDistribution, warnings);
        return List.copyOf(warnings);
    }

    private Set<String> validateTopics(List<GoldTopic> topics, List<String> warnings) {
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (GoldTopic topic : topics) {
            if (topic.id() == null || topic.id().isBlank()) throw invalid("黄金集知识点 ID 不能为空");
            if (topic.name() == null || topic.name().isBlank()) throw invalid("黄金集知识点名称不能为空: " + topic.id());
            if (!ids.add(topic.id())) throw invalid("黄金集知识点 ID 重复: " + topic.id());
            if (!names.add(topic.name())) warnings.add("重复知识点名称: " + topic.name());
            if (blank(topic.type())) warnings.add("知识点 type 为空: " + topic.id());
            if (blank(topic.domain())) warnings.add("知识点 domain 为空: " + topic.id());
            if (blank(topic.description())) warnings.add("知识点 description 为空: " + topic.id());
        }
        return ids;
    }

    private void validateDependencies(Set<String> ids, List<GoldDependency> dependencies, List<String> warnings) {
        Set<String> edges = new HashSet<>();
        Set<String> warnedExternalIds = new HashSet<>();
        for (GoldDependency edge : dependencies) {
            if (blank(edge.topicId())) throw invalid("黄金集依赖目标 topicId 不能为空");
            if (blank(edge.prerequisiteId())) throw invalid("黄金集依赖 prerequisiteId 不能为空");
            if (!ids.contains(edge.topicId())) throw invalid("依赖关系目标不在本册知识点中: " + edge.topicId());
            if (edge.topicId().equals(edge.prerequisiteId())) throw invalid("黄金集存在自环: " + edge.topicId());
            String key = edge.prerequisiteId() + "->" + edge.topicId();
            if (!edges.add(key)) warnings.add("重复依赖边: " + key);
            if (!ids.contains(edge.prerequisiteId()) && warnedExternalIds.add(edge.prerequisiteId())) {
                warnings.add("跨册依赖端点未在本册解析: " + edge.prerequisiteId());
            }
        }
    }

    private void validateDag(Set<String> ids, List<GoldDependency> dependencies) {
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> outgoing = new HashMap<>();
        ids.forEach(id -> indegree.put(id, 0));
        dependencies.stream().filter(edge -> ids.contains(edge.prerequisiteId())).forEach(edge -> {
            outgoing.computeIfAbsent(edge.prerequisiteId(), ignored -> new ArrayList<>()).add(edge.topicId());
            indegree.compute(edge.topicId(), (ignored, value) -> value + 1);
        });
        ArrayDeque<String> queue = new ArrayDeque<>();
        indegree.forEach((id, degree) -> { if (degree == 0) queue.add(id); });
        int visited = 0;
        while (!queue.isEmpty()) {
            String id = queue.remove();
            visited++;
            for (String target : outgoing.getOrDefault(id, List.of())) {
                int degree = indegree.compute(target, (ignored, value) -> value - 1);
                if (degree == 0) queue.add(target);
            }
        }
        if (visited != ids.size()) throw invalid("黄金集内部依赖图存在环路");
    }

    private void validateManifest(List<GoldTopic> topics, Map<String, Long> expected, List<String> warnings) {
        if (expected == null || expected.isEmpty()) return;
        Map<String, Long> actual = topics.stream().collect(Collectors.groupingBy(GoldTopic::type, Collectors.counting()));
        expected.forEach((type, count) -> {
            if (!actual.getOrDefault(type, 0L).equals(count)) {
                warnings.add("manifest 类型统计与 topics.json 不一致: " + type);
            }
        });
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.A0002, message);
    }
}
