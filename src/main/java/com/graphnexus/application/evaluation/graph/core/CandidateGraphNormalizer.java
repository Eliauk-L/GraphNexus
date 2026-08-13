package com.graphnexus.application.evaluation.graph.core;

import com.graphnexus.application.evaluation.graph.model.CandidateDependency;
import com.graphnexus.application.evaluation.graph.model.CandidateGraph;
import com.graphnexus.application.evaluation.graph.model.CandidateGraphNormalizationResult;
import com.graphnexus.application.evaluation.graph.model.CandidateTopic;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CandidateGraphNormalizer {

    private final TopicNameNormalizer nameNormalizer;

    public CandidateGraphNormalizationResult normalize(CandidateGraph raw) {
        Map<String, CandidateTopic> topics = new LinkedHashMap<>();
        Map<String, String> inputToCanonical = new LinkedHashMap<>();
        Map<String, String> idByName = new LinkedHashMap<>();
        Set<String> inputIds = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();
        int empty = 0;
        int merged = 0;
        int generated = 0;
        for (CandidateTopic topic : raw.topics()) {
            String normalizedName = nameNormalizer.normalize(topic.name());
            if (normalizedName.isBlank()) {
                empty++;
                continue;
            }
            String inputId = topic.tempId() == null || topic.tempId().isBlank()
                    ? "generated-" + (++generated) : topic.tempId().trim();
            if (!inputIds.add(inputId)) {
                throw new BusinessException(ErrorCode.A0002, "候选知识点 tempId 重复: " + inputId);
            }
            String canonical = idByName.get(normalizedName);
            if (canonical == null) {
                canonical = inputId;
                idByName.put(normalizedName, canonical);
                topics.put(canonical, new CandidateTopic(canonical, normalizedName,
                        clean(topic.type()), clean(topic.domain()), clean(topic.description())));
            } else {
                merged++;
            }
            inputToCanonical.put(inputId, canonical);
        }

        int selfLoops = 0;
        int duplicateEdges = 0;
        int dangling = 0;
        Set<EdgeKey> seen = new LinkedHashSet<>();
        List<CandidateDependency> dependencies = new ArrayList<>();
        for (CandidateDependency edge : raw.dependencies()) {
            String source = inputToCanonical.get(edge.prerequisiteTempId());
            String target = inputToCanonical.get(edge.topicTempId());
            if (source == null || target == null) {
                dangling++;
            } else if (source.equals(target)) {
                selfLoops++;
            } else if (!seen.add(new EdgeKey(source, target))) {
                duplicateEdges++;
            } else {
                dependencies.add(new CandidateDependency(source, target, clean(edge.strength())));
            }
        }
        if (empty > 0) warnings.add("已删除名称为空的候选知识点: " + empty);
        if (merged > 0) warnings.add("已合并重复候选知识点: " + merged);
        if (selfLoops > 0) warnings.add("已删除候选自环: " + selfLoops);
        if (duplicateEdges > 0) warnings.add("已合并重复候选依赖: " + duplicateEdges);
        if (dangling > 0) warnings.add("已删除端点不存在的候选依赖: " + dangling);
        return new CandidateGraphNormalizationResult(new CandidateGraph(new ArrayList<>(topics.values()), dependencies),
                empty, merged, selfLoops, duplicateEdges, dangling, warnings);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private record EdgeKey(String source, String target) {
    }
}
