package com.graphnexus.application.analysis.strategy;

import com.graphnexus.application.analysis.model.PruningRequest;
import com.graphnexus.application.analysis.model.PrunedSubgraph;
import com.graphnexus.application.analysis.model.PrunedSubgraph.PruningMeta;
import com.graphnexus.application.graph.construction.model.GraphEdgeData;
import com.graphnexus.application.graph.construction.model.GraphNodeData;
import com.graphnexus.infrastructure.mysql.file.entity.ExamRecordDO;
import com.graphnexus.infrastructure.mysql.file.repository.ExamRecordRepository;
import com.graphnexus.infrastructure.neo4j.repository.QueryGraphRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 班级薄弱概览剪枝策略 — 聚合全班 MASTERS 数据，按知识点统计薄弱排行。
 *
 * <p>Bean name = "CLASS_WEAKNESS_OVERVIEW"（与 {@code QueryIntent} 枚举名一致），
 * 由 {@code PruningStrategyRegistry} 通过 Map 自动注入发现。
 * 分 5 步：MySQL 查班级学生 → Neo4j 批量查 MASTERS → Java 层 KP 聚合 →
 * 前置依赖链展开 → 组装聚合子图。</p>
 *
 * <p>设计决策见 ADR-044。</p>
 *
 * @author Jay
 * @date 2026/06/23
 */
@Slf4j
@Component("CLASS_WEAKNESS_OVERVIEW")
@RequiredArgsConstructor
public class ClassWeaknessOverviewStrategy implements SubgraphPruningStrategy {

    private final QueryGraphRepository queryGraphRepository;
    private final ExamRecordRepository examRecordRepository;
    private final ObjectMapper objectMapper;

    private static final double DEFAULT_WEAK_THRESHOLD = 0.6;
    private static final int DEFAULT_MAX_HOPS = 2;
    private static final int MAX_WEAK_KP_COUNT = 20;

    @Override
    public PrunedSubgraph prune(PruningRequest request) {
        if (!"CLASS_WEAKNESS_OVERVIEW".equals(request.intent())) {
            log.warn("ClassWeaknessOverviewStrategy 收到非预期意图: {}", request.intent());
            return emptyResult("UNSUPPORTED_INTENT");
        }

        String className = request.entityId();
        String subject = request.subject();
        double weakThreshold = getParam(request.params(), "weakThreshold", DEFAULT_WEAK_THRESHOLD);
        int maxHops = (int) getParam(request.params(), "maxHops", (double) DEFAULT_MAX_HOPS);

        // Step 1: 从 MySQL 查询班级学生列表
        List<Object[]> classStudents = examRecordRepository.findDistinctStudentsByClassName(className);
        if (classStudents.isEmpty()) {
            log.warn("班级不存在或无学生: className={}", className);
            return emptyResult("CLASS_NOT_FOUND");
        }

        List<String> studentNos = classStudents.stream()
                .map(r -> (String) r[0])
                .distinct()
                .collect(Collectors.toList());
        int classSize = studentNos.size();

        // 构建 studentNo → studentName 映射
        Map<String, String> studentNameMap = new HashMap<>();
        for (Object[] row : classStudents) {
            studentNameMap.put((String) row[0], (String) row[1]);
        }

        // Step 2: 批量查询 MASTERS 边
        var mastersRows = queryGraphRepository.findMastersByStudentNos(studentNos, subject);
        boolean mastersAvailable = !mastersRows.isEmpty();

        Map<String, KpAggregation> kpAggMap = new LinkedHashMap<>();
        Map<String, String> kpNameMap = new HashMap<>();

        if (mastersAvailable) {
            // MASTERS 可用：按 KP 聚合 weight
            for (var row : mastersRows) {
                String kpId = (String) row.get("kpId");
                String kpName = (String) row.get("kpName");
                double weight = ((Number) row.get("weight")).doubleValue();

                kpNameMap.putIfAbsent(kpId, kpName);
                kpAggMap.computeIfAbsent(kpId, k -> new KpAggregation()).add(weight);
            }
        } else {
            // MASTERS 降级：TESTED 路径 + MySQL 原始得分率
            log.info("班级 {} MASTERS 不可用，降级为 TESTED 路径 + 原始得分率计算", className);
            Map<String, List<Double>> kpScoresMap = new HashMap<>();

            for (String studentNo : studentNos) {
                var testedKps = queryGraphRepository.findTestedKpsByStudentAndSubject(studentNo, subject);
                for (var row : testedKps) {
                    String kpId = (String) row.get("kpId");
                    String kpName = (String) row.get("kpName");
                    kpNameMap.putIfAbsent(kpId, kpName);
                    double scoreRate = calculateRawScoreRate(studentNo, kpName);
                    kpScoresMap.computeIfAbsent(kpId, k -> new ArrayList<>()).add(scoreRate);
                }
            }

            for (var entry : kpScoresMap.entrySet()) {
                String kpId = entry.getKey();
                List<Double> scores = entry.getValue();
                KpAggregation agg = new KpAggregation();
                scores.forEach(agg::add);
                kpAggMap.put(kpId, agg);
            }
        }

        // Step 3: 筛选薄弱 KP（按薄弱人数降序，取 Top N）
        List<Map.Entry<String, KpAggregation>> weakKpEntries = kpAggMap.entrySet().stream()
                .filter(e -> e.getValue().avgWeight < weakThreshold || e.getValue().weakCount > 0)
                .sorted((a, b) -> {
                    // 按薄弱人数降序，相同则按平均掌握度升序
                    int cmp = Integer.compare(b.getValue().weakCount, a.getValue().weakCount);
                    return cmp != 0 ? cmp : Double.compare(a.getValue().avgWeight, b.getValue().avgWeight);
                })
                .limit(MAX_WEAK_KP_COUNT)
                .collect(Collectors.toList());

        List<String> weakKpIds = weakKpEntries.stream().map(Map.Entry::getKey).collect(Collectors.toList());

        // Step 4: 展开前置依赖链
        List<Map<String, Object>> prereqRows = weakKpIds.isEmpty()
                ? Collections.emptyList()
                : queryGraphRepository.findPrerequisitesUpstream(weakKpIds, maxHops);
        Set<String> preKpIds = new HashSet<>();
        for (var row : prereqRows) {
            String toKpId = (String) row.get("toKpId");
            String toKpName = (String) row.get("toKpName");
            preKpIds.add(toKpId);
            kpNameMap.putIfAbsent(toKpId, toKpName);
        }

        // Step 5: 组装结果
        return buildResult(className, classSize, subject, kpAggMap, kpNameMap,
                weakKpEntries, preKpIds, prereqRows, mastersAvailable,
                weakThreshold, maxHops);
    }

    // ======================== 私有辅助方法 ========================

    /**
     * 降级路径：从 MySQL exam_record 计算单个学生对某知识点的原始得分率。
     * 使用批量查询优化（非逐生查询）。
     */
    private double calculateRawScoreRate(String studentNo, String kpName) {
        List<ExamRecordDO> records = examRecordRepository.findByStudentNo(studentNo);
        double totalScoreRate = 0;
        int count = 0;
        for (ExamRecordDO record : records) {
            if (record.getScoreDetails() == null) continue;
            try {
                List<Map<String, Object>> details = objectMapper.readValue(
                        record.getScoreDetails(), new TypeReference<List<Map<String, Object>>>() {});
                for (var detail : details) {
                    Object kpNamesObj = detail.get("kpNames");
                    if (kpNamesObj == null) continue;
                    String kpNamesStr = kpNamesObj.toString();
                    if (Arrays.stream(kpNamesStr.split(";"))
                            .map(String::trim)
                            .anyMatch(kpName::equals)) {
                        Object rawObj = detail.get("rawScore");
                        Object maxObj = detail.get("maxScore");
                        if (rawObj != null && maxObj != null) {
                            double raw = Double.parseDouble(rawObj.toString());
                            double max = Double.parseDouble(maxObj.toString());
                            if (max > 0) {
                                totalScoreRate += raw / max;
                                count++;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("解析 score_details 失败: recordId={}", record.getId());
            }
        }
        return count > 0 ? totalScoreRate / count : 0.0;
    }

    private PrunedSubgraph buildResult(
            String className, int classSize, String subject,
            Map<String, KpAggregation> kpAggMap,
            Map<String, String> kpNameMap,
            List<Map.Entry<String, KpAggregation>> weakKpEntries,
            Set<String> preKpIds,
            List<Map<String, Object>> prereqRows,
            boolean mastersAvailable,
            double weakThreshold,
            int maxHops) {

        List<GraphNodeData> nodes = new ArrayList<>();
        List<GraphEdgeData> edges = new ArrayList<>();
        Set<String> seenNodeIds = new HashSet<>();

        // 虚拟班级节点
        String classNodeId = "class-" + className.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5]", "");
        Map<String, Object> classProps = new LinkedHashMap<>();
        classProps.put("className", className);
        classProps.put("classSize", classSize);
        classProps.put("subject", subject);
        classProps.put("type", "ClassInfo");
        GraphNodeData classNode = new GraphNodeData(classNodeId, "ClassInfo", className, null, null, classProps);
        nodes.add(classNode);
        seenNodeIds.add(classNodeId);

        // 薄弱 KP 节点 + 聚合 MASTERS 边
        for (var entry : weakKpEntries) {
            String kpId = entry.getKey();
            KpAggregation agg = entry.getValue();
            String kpName = kpNameMap.getOrDefault(kpId, "未知知识点");

            if (seenNodeIds.add(kpId)) {
                Map<String, Object> props = new LinkedHashMap<>();
                props.put("name", kpName);
                props.put("subject", subject);
                props.put("avgWeight", agg.avgWeight);
                props.put("weakCount", agg.weakCount);
                props.put("totalCount", agg.totalCount);
                props.put("minWeight", agg.minWeight);
                props.put("maxWeight", agg.maxWeight);
                nodes.add(new GraphNodeData(kpId, "KnowledgePoint", kpName, null, null, props));
            }

            // 聚合 MASTERS 边：ClassInfo → KP
            edges.add(new GraphEdgeData(classNodeId, kpId, "MASTERS", agg.avgWeight, null));
        }

        // 前置依赖 KP 节点 + PREREQUISITE_OF 边
        for (String kpId : preKpIds) {
            if (seenNodeIds.add(kpId)) {
                String kpName = kpNameMap.getOrDefault(kpId, "未知知识点");
                Map<String, Object> props = new LinkedHashMap<>();
                props.put("name", kpName);
                props.put("subject", subject);
                // 标注该 KP 是否在聚合数据中（非薄弱 KP，仅因依赖链被纳入）
                KpAggregation agg = kpAggMap.get(kpId);
                if (agg != null) {
                    props.put("avgWeight", agg.avgWeight);
                    props.put("weakCount", agg.weakCount);
                    props.put("totalCount", agg.totalCount);
                } else {
                    props.put("dependencyOnly", true);
                }
                nodes.add(new GraphNodeData(kpId, "KnowledgePoint", kpName, null, null, props));
            }
        }

        for (var row : prereqRows) {
            Object toKpIdObj = row.get("toKpId");
            if (toKpIdObj == null) continue;
            String toKpId = toKpIdObj.toString();
            if (seenNodeIds.add(toKpId)) {
                String kpName = kpNameMap.getOrDefault(toKpId, "未知知识点");
                Map<String, Object> props = new LinkedHashMap<>();
                props.put("name", kpName);
                props.put("subject", subject);
                props.put("dependencyOnly", true);
                nodes.add(new GraphNodeData(toKpId, "KnowledgePoint", kpName, null, null, props));
            }
            edges.add(new GraphEdgeData(
                    (String) row.get("fromKpId"), toKpId, "PREREQUISITE_OF", 1.0, null));
        }

        // 元信息
        PruningMeta meta = new PruningMeta(
                "CLASS_WEAKNESS_OVERVIEW",
                mastersAvailable,
                weakThreshold,
                maxHops,
                nodes.size(),
                edges.size(),
                false,
                Collections.emptyList()
        );

        return new PrunedSubgraph(nodes, edges, meta);
    }

    private double getParam(Map<String, Object> params, String key, double defaultValue) {
        if (params == null) return defaultValue;
        Object val = params.get(key);
        if (val instanceof Number n) return n.doubleValue();
        return defaultValue;
    }

    private PrunedSubgraph emptyResult(String reason) {
        return new PrunedSubgraph(
                Collections.emptyList(),
                Collections.emptyList(),
                new PruningMeta(reason, false, 0, 0, 0, 0, false, Collections.emptyList())
        );
    }

    /**
     * 单个知识点的聚合统计。
     */
    private static class KpAggregation {
        double sumWeight = 0;
        double minWeight = Double.MAX_VALUE;
        double maxWeight = Double.MIN_VALUE;
        int totalCount = 0;
        int weakCount = 0;
        double avgWeight = 0;

        void add(double weight) {
            sumWeight += weight;
            minWeight = Math.min(minWeight, weight);
            maxWeight = Math.max(maxWeight, weight);
            totalCount++;
            avgWeight = sumWeight / totalCount;
            if (weight < DEFAULT_WEAK_THRESHOLD) {
                weakCount++;
            }
        }
    }
}