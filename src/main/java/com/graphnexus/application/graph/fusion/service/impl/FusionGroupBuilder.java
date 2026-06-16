package com.graphnexus.application.graph.fusion.service.impl;

import com.graphnexus.application.graph.fusion.model.FusionGroup;
import com.graphnexus.application.graph.fusion.model.KpCandidate;
import com.graphnexus.application.graph.fusion.strategy.KpMatchingStrategy;
import com.graphnexus.infrastructure.neo4j.repository.GraphNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * KP 融合分组构建器 — Union-Find 聚类 + 选主 KP + 边重定向执行。
 *
 * <p>从 {@link FusionServiceImpl} 中提取，职责单一：输入 KP 列表 → 输出融合组 + 执行合并。</p>
 *
 * @author Jay
 * @date 2026/06/16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FusionGroupBuilder {

    private final GraphNodeRepository graphNodeRepository;

    /**
     * 构建融合分组：两两匹配 → Union-Find 聚类 → 选主 KP。
     *
     * @param kps       Neo4j 查询出的 KP 属性列表
     * @param subject   学科
     * @param matcher   匹配策略
     * @param threshold 融合阈值
     * @return 融合组列表（单 KP 组已过滤）
     */
    public List<FusionGroup> build(List<Map<String, Object>> kps, String subject,
                                    KpMatchingStrategy matcher, double threshold) {
        if (kps.size() < 2) return Collections.emptyList();

        List<KpCandidate> candidates = kps.stream().map(m -> new KpCandidate(
                (String) m.get("name"),
                subject,
                (String) m.get("documentId"),
                (String) m.get("fusionSource")
        )).collect(Collectors.toList());

        // Union-Find 聚类
        int n = candidates.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (matcher.match(candidates.get(i), candidates.get(j)) >= threshold) {
                    union(parent, i, j);
                }
            }
        }

        // 按根节点收集分组
        Map<Integer, List<Integer>> groupMap = new HashMap<>();
        for (int i = 0; i < n; i++) {
            groupMap.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(i);
        }

        // 构建 FusionGroup（过滤单 KP 组）
        List<FusionGroup> groups = new ArrayList<>();
        int groupId = 0;
        for (var entry : groupMap.entrySet()) {
            List<Integer> indices = entry.getValue();
            if (indices.size() < 2) continue;

            int masterIdx = selectMaster(indices, kps);

            List<String> sourceIds = new ArrayList<>();
            List<Map<String, Object>> sourceProps = new ArrayList<>();
            Map<String, Object> targetProps = buildTargetProps(indices, masterIdx, kps);
            for (int idx : indices) {
                if (idx != masterIdx) {
                    sourceIds.add((String) kps.get(idx).get("id"));
                    sourceProps.add(kps.get(idx));
                }
            }

            groups.add(new FusionGroup(
                    groupId++, sourceIds, sourceProps,
                    (String) kps.get(masterIdx).get("id"),
                    targetProps, List.of()));
        }
        return groups;
    }

    /**
     * 执行融合：边重定向 → 删除冗余 KP → 更新规范 KP。
     */
    public void merge(List<FusionGroup> groups) {
        for (FusionGroup group : groups) {
            for (String sourceId : group.sourceKpIds()) {
                graphNodeRepository.redirectEdges(sourceId, group.targetKpId());
            }
            graphNodeRepository.deleteKnowledgePoints(group.sourceKpIds());
            graphNodeRepository.createNodeWithProperties("KnowledgePoint", group.targetKpProps());
            log.debug("融合组 {}: {} → {}", group.groupId(), group.sourceKpIds(), group.targetKpId());
        }
    }

    // ======================== 私有方法 ========================

    /** 选主 KP：documentId 非空优先 → fusionSource 含 DOCUMENT 优先 → 第一个 */
    private int selectMaster(List<Integer> indices, List<Map<String, Object>> kps) {
        int masterIdx = indices.get(0);
        String masterDocId = (String) kps.get(masterIdx).get("documentId");
        for (int idx : indices) {
            String docId = (String) kps.get(idx).get("documentId");
            String fs = (String) kps.get(idx).get("fusionSource");
            if (docId != null && !docId.isEmpty() && (masterDocId == null || masterDocId.isEmpty())) {
                masterIdx = idx;
                masterDocId = docId;
            } else if (fs != null && fs.contains("DOCUMENT") && masterDocId == null) {
                masterIdx = idx;
                masterDocId = docId;
            }
        }
        return masterIdx;
    }

    /** 构建规范 KP 属性（拼接 fusionSource） */
    private Map<String, Object> buildTargetProps(List<Integer> indices, int masterIdx,
                                                  List<Map<String, Object>> kps) {
        Map<String, Object> targetProps = new HashMap<>(kps.get(masterIdx));
        String targetFs = (String) targetProps.get("fusionSource");
        for (int idx : indices) {
            if (idx != masterIdx) {
                String fs = (String) kps.get(idx).get("fusionSource");
                if (fs != null && !fs.isEmpty() && (targetFs == null || !targetFs.contains(fs))) {
                    targetFs = (targetFs == null || targetFs.isEmpty()) ? fs : targetFs + "," + fs;
                }
            }
        }
        targetProps.put("fusionSource", targetFs);
        return targetProps;
    }

    private int find(int[] parent, int x) {
        if (parent[x] != x) parent[x] = find(parent, parent[x]);
        return parent[x];
    }

    private void union(int[] parent, int a, int b) {
        int ra = find(parent, a), rb = find(parent, b);
        if (ra != rb) parent[ra] = rb;
    }
}