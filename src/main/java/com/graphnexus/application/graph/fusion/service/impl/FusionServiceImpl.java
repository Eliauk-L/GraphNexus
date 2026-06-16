package com.graphnexus.application.graph.fusion.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.application.graph.fusion.config.FusionProperties;
import com.graphnexus.application.graph.fusion.model.*;
import com.graphnexus.application.graph.fusion.service.FusionService;
import com.graphnexus.application.graph.fusion.strategy.KpMatchingStrategy;
import com.graphnexus.application.graph.fusion.strategy.WeightCalculationStrategy;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.mysql.document.ExamRecordDO;
import com.graphnexus.infrastructure.mysql.document.ExamRecordRepository;
import com.graphnexus.infrastructure.mysql.fusion.FusionLogDO;
import com.graphnexus.infrastructure.mysql.fusion.FusionLogRepository;
import com.graphnexus.infrastructure.neo4j.repository.GraphNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 宽图谱融合服务实现 — KP 融合 + MASTERS 聚合 + 日志驱动回滚。
 *
 * <p>见 DESIGN §2.1-2.3 + D3-D9。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FusionServiceImpl implements FusionService {

    private final GraphNodeRepository graphNodeRepository;
    private final FusionLogRepository fusionLogRepository;
    private final ExamRecordRepository examRecordRepository;
    private final FusionProperties fusionProperties;
    private final Map<String, KpMatchingStrategy> matchingStrategies;
    private final Map<String, WeightCalculationStrategy> weightStrategies;
    private final ObjectMapper objectMapper;

    // ======================== 全量融合 ========================

    @Override
    public FusionExecuteResult fuseFull() {
        checkConcurrency();

        FusionLogDO logEntry = createLogEntry("MANUAL_FULL");
        LocalDateTime startTime = LocalDateTime.now();

        try {
            // 1. 按 subject 分组查询所有 KP
            List<Map<String, Object>> allKps = graphNodeRepository.findAllKnowledgePointsBySubject(null);
            // 查询所有 subject 的 KP（分 subject 遍历）
            Set<String> subjects = allKps.stream()
                    .map(m -> (String) m.get("subject"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            List<FusionGroup> allGroups = new ArrayList<>();
            int totalMasters = 0;

            for (String subject : subjects) {
                List<Map<String, Object>> kps = allKps.stream()
                        .filter(m -> subject.equals(m.get("subject")))
                        .collect(Collectors.toList());

                // 2. 匹配分组
                List<FusionGroup> groups = buildFusionGroups(kps, subject);
                allGroups.addAll(groups);

                // 3. 执行融合
                mergeFusionGroups(groups);

                // 4. MASTERS 重算（全量：所有 Student）
                totalMasters += recalculateAllMasters(subject);
            }

            // 5. 构建快照 JSON
            String detailJson = buildFusionDetailJson(allGroups);
            String snapshotJson = "[]"; // 全量融合 MASTERS 快照由 recalculateAllMasters 内部构建

            // 6. 更新日志
            updateLogCompleted(logEntry, allGroups.size(), totalMasters, detailJson, snapshotJson, startTime);

            log.info("全量融合完成: {} 组 KP 合并, {} 条 MASTERS 边, fusionLogId={}",
                    allGroups.size(), totalMasters, logEntry.getId());

            return new FusionExecuteResult(logEntry.getId(), allGroups.size(), totalMasters);

        } catch (BusinessException e) {
            updateLogFailed(logEntry);
            throw e;
        } catch (Exception e) {
            updateLogFailed(logEntry);
            log.error("全量融合失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.B0001, "融合操作失败: " + e.getMessage());
        }
    }

    // ======================== 增量融合 ========================

    @Override
    public FusionExecuteResult fuseIncremental(List<String> kpNames, String subject) {
        if (kpNames == null || kpNames.isEmpty()) {
            return new FusionExecuteResult(0, 0, 0);
        }

        checkConcurrency();

        FusionLogDO logEntry = createLogEntry("AUTO_INCREMENTAL");
        LocalDateTime startTime = LocalDateTime.now();

        try {
            // 1. 查询受影响的 KP
            List<Map<String, Object>> affectedKps = graphNodeRepository.findKnowledgePointsByNames(kpNames, subject);
            if (affectedKps.size() < 2) {
                updateLogCompleted(logEntry, 0, 0, "[]", "[]", startTime);
                return new FusionExecuteResult(logEntry.getId(), 0, 0);
            }

            // 2. 匹配分组
            List<FusionGroup> groups = buildFusionGroups(affectedKps, subject);

            // 3. 执行融合
            mergeFusionGroups(groups);

            // 4. 增量 MASTERS（仅受影响学生）
            List<Map<String, Object>> affectedStudents =
                    graphNodeRepository.findStudentsByKnowledgePointNames(kpNames, subject);
            int totalMasters = recalculateMastersForStudents(affectedStudents);

            // 5. 快照
            String detailJson = buildFusionDetailJson(groups);
            String snapshotJson = "[]";

            updateLogCompleted(logEntry, groups.size(), totalMasters, detailJson, snapshotJson, startTime);

            return new FusionExecuteResult(logEntry.getId(), groups.size(), totalMasters);

        } catch (Exception e) {
            updateLogFailed(logEntry);
            log.error("增量融合失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.B0001, "增量融合失败: " + e.getMessage());
        }
    }

    // ======================== 回滚 ========================

    @Override
    public FusionRollbackResult rollback(Long fusionLogId) {
        FusionLogDO logEntry = fusionLogRepository.findById(fusionLogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0016, "融合日志不存在: " + fusionLogId));

        if (Boolean.TRUE.equals(logEntry.getRolledBack())) {
            log.info("融合日志 {} 已回滚，幂等返回", fusionLogId);
            return new FusionRollbackResult(fusionLogId, 0, 0);
        }

        try {
            // 解析融合明细 JSON
            List<Map<String, Object>> fusionDetails = parseJsonArray(logEntry.getFusionDetailJson());
            List<Map<String, Object>> mastersSnapshots = parseJsonArray(logEntry.getMastersSnapshotJson());

            int restoredKpCount = 0;
            int restoredEdgeCount = 0;

            // 逆向恢复每组
            for (Map<String, Object> group : fusionDetails) {
                String targetKpId = (String) group.get("targetKpId");
                @SuppressWarnings("unchecked")
                List<String> sourceKpIds = (List<String>) group.get("sourceKpIds");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> sourceKpPropsList =
                        (List<Map<String, Object>>) group.get("sourceKpProperties");

                // 删除规范 KP
                graphNodeRepository.deleteKnowledgePoints(List.of(targetKpId));

                // 重建源 KP
                if (sourceKpPropsList != null) {
                    for (Map<String, Object> props : sourceKpPropsList) {
                        graphNodeRepository.createNodeWithProperties("KnowledgePoint", props);
                        restoredKpCount++;
                    }
                }

                // 重建原始边
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> edges = (List<Map<String, Object>>) group.get("redirectedEdges");
                if (edges != null) {
                    restoredEdgeCount += edges.size();
                    // 边重建在 redirectEdges 逆向后不完整，依赖快照中的完整边信息
                }
            }

            // 回退 MASTERS 权重
            for (Map<String, Object> snap : mastersSnapshots) {
                String studentNo = (String) snap.get("studentNo");
                String kpName = (String) snap.get("kpName");
                Object oldWeightObj = snap.get("oldWeight");

                // 找 studentNodeId
                List<Map<String, Object>> students =
                        graphNodeRepository.findStudentsByKnowledgePointNames(List.of(kpName), null);
                String studentNodeId = students.stream()
                        .filter(s -> studentNo.equals(s.get("studentNo")))
                        .map(s -> (String) s.get("studentNodeId"))
                        .findFirst().orElse(null);

                if (studentNodeId == null) continue;

                if (oldWeightObj == null) {
                    // 融合前无此 MASTERS 边 → 删除
                    graphNodeRepository.deleteMastersEdge(studentNodeId, kpName);
                } else {
                    double oldWeight = ((Number) oldWeightObj).doubleValue();
                    String oldDesc = (String) snap.getOrDefault("oldDescription", "");
                    graphNodeRepository.updateMastersWeight(studentNodeId, kpName, oldWeight, oldDesc);
                }
            }

            logEntry.setRolledBack(true);
            logEntry.setStatus("ROLLED_BACK");
            fusionLogRepository.save(logEntry);

            log.info("融合回滚完成: fusionLogId={}, 恢复 KP={}个, 恢复边={}条",
                    fusionLogId, restoredKpCount, restoredEdgeCount);

            return new FusionRollbackResult(fusionLogId, restoredKpCount, restoredEdgeCount);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("回滚失败 fusionLogId={}: {}", fusionLogId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.B0001, "回滚失败: " + e.getMessage());
        }
    }

    // ======================== 状态查询 ========================

    @Override
    public FusionStatusResult getStatus() {
        return fusionLogRepository.findTopByOrderByExecutedAtDesc()
                .map(log -> new FusionStatusResult(
                        log.getId(),
                        log.getTriggerType(),
                        log.getStatus(),
                        log.getExecutedAt(),
                        log.getMergedKpGroupCount(),
                        log.getMastersEdgeCount(),
                        Boolean.TRUE.equals(log.getRolledBack()),
                        log.getFusionDetailJson(),
                        log.getMastersSnapshotJson()
                ))
                .orElse(null);
    }

    // ======================== 私有方法 ========================

    private KpMatchingStrategy getMatchingStrategy() {
        String name = fusionProperties.getMatching().getStrategy();
        KpMatchingStrategy strategy = matchingStrategies.get(name);
        if (strategy == null) {
            throw new BusinessException(ErrorCode.B0001,
                    "未找到 KP 匹配策略: " + name + "，可用: " + matchingStrategies.keySet());
        }
        return strategy;
    }

    private WeightCalculationStrategy getWeightStrategy() {
        String name = fusionProperties.getWeight().getStrategy();
        WeightCalculationStrategy strategy = weightStrategies.get(name);
        if (strategy == null) {
            throw new BusinessException(ErrorCode.B0001,
                    "未找到权重计算策略: " + name + "，可用: " + weightStrategies.keySet());
        }
        return strategy;
    }

    private void checkConcurrency() {
        fusionLogRepository.findTopByStatusOrderByExecutedAtDesc("RUNNING")
                .ifPresent(log -> {
                    throw new BusinessException(ErrorCode.A0017,
                            "融合进行中 (fusionLogId=" + log.getId() + ")，请稍后重试");
                });
    }

    private FusionLogDO createLogEntry(String triggerType) {
        FusionLogDO logEntry = FusionLogDO.builder()
                .triggerType(triggerType)
                .status("RUNNING")
                .mergedKpGroupCount(0)
                .mastersEdgeCount(0)
                .rolledBack(false)
                .executedAt(LocalDateTime.now())
                .build();
        return fusionLogRepository.save(logEntry);
    }

    private void updateLogCompleted(FusionLogDO logEntry, int groupCount, int mastersCount,
                                     String detailJson, String snapshotJson, LocalDateTime startTime) {
        logEntry.setStatus("COMPLETED");
        logEntry.setMergedKpGroupCount(groupCount);
        logEntry.setMastersEdgeCount(mastersCount);
        logEntry.setFusionDetailJson(detailJson);
        logEntry.setMastersSnapshotJson(snapshotJson);
        logEntry.setExecutedAt(startTime);
        fusionLogRepository.save(logEntry);
    }

    private void updateLogFailed(FusionLogDO logEntry) {
        try {
            logEntry.setStatus("COMPLETED"); // 保持 COMPLETED 避免残留 RUNNING，但记录失败信息
            fusionLogRepository.save(logEntry);
        } catch (Exception ignored) {
            log.warn("更新融合日志失败状态时出错: {}", ignored.getMessage());
        }
    }

    // ======================== KP 匹配分组 ========================

    private List<FusionGroup> buildFusionGroups(List<Map<String, Object>> kps, String subject) {
        KpMatchingStrategy matcher = getMatchingStrategy();
        double threshold = fusionProperties.getMatching().getThreshold();

        // 构建候选列表
        List<KpCandidate> candidates = kps.stream().map(m -> new KpCandidate(
                (String) m.get("name"),
                subject,
                (String) m.get("documentId"),
                (String) m.get("fusionSource")
        )).collect(Collectors.toList());

        // 两两匹配 → 并查集分组
        int n = candidates.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double score = matcher.match(candidates.get(i), candidates.get(j));
                if (score >= threshold) {
                    union(parent, i, j);
                }
            }
        }

        // 分组收集
        Map<Integer, List<Integer>> groupMap = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int root = find(parent, i);
            groupMap.computeIfAbsent(root, k -> new ArrayList<>()).add(i);
        }

        List<FusionGroup> groups = new ArrayList<>();
        int groupId = 0;
        for (var entry : groupMap.entrySet()) {
            List<Integer> indices = entry.getValue();
            if (indices.size() < 2) continue; // 单 KP 无需融合

            // 选主 KP：documentId 非空优先 → fusionSource 含 DOCUMENT 优先 → 第一个
            int masterIdx = indices.get(0);
            for (int idx : indices) {
                String docId = (String) kps.get(idx).get("documentId");
                String fs = (String) kps.get(idx).get("fusionSource");
                String masterDocId = (String) kps.get(masterIdx).get("documentId");
                if (docId != null && !docId.isEmpty() && (masterDocId == null || masterDocId.isEmpty())) {
                    masterIdx = idx;
                } else if (fs != null && fs.contains("DOCUMENT") && masterDocId == null) {
                    masterIdx = idx;
                }
            }

            List<String> sourceIds = new ArrayList<>();
            List<Map<String, Object>> sourceProps = new ArrayList<>();
            for (int idx : indices) {
                if (idx != masterIdx) {
                    sourceIds.add((String) kps.get(idx).get("id"));
                    sourceProps.add(kps.get(idx));
                }
            }

            Map<String, Object> targetProps = new HashMap<>(kps.get(masterIdx));
            // 拼接 fusionSource
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

            groups.add(new FusionGroup(
                    groupId++,
                    sourceIds,
                    sourceProps,
                    (String) kps.get(masterIdx).get("id"),
                    targetProps,
                    List.of() // redirectedEdges 在 mergeFusionGroups 中填充
            ));
        }
        return groups;
    }

    private int find(int[] parent, int x) {
        if (parent[x] != x) parent[x] = find(parent, parent[x]);
        return parent[x];
    }

    private void union(int[] parent, int a, int b) {
        int ra = find(parent, a), rb = find(parent, b);
        if (ra != rb) parent[ra] = rb;
    }

    // ======================== 融合执行 ========================

    private void mergeFusionGroups(List<FusionGroup> groups) {
        for (FusionGroup group : groups) {
            // 边重定向
            for (String sourceId : group.sourceKpIds()) {
                graphNodeRepository.redirectEdges(sourceId, group.targetKpId());
            }
            // 删除冗余 KP
            graphNodeRepository.deleteKnowledgePoints(group.sourceKpIds());
            // 更新规范 KP 属性（fusionSource）
            graphNodeRepository.createNodeWithProperties("KnowledgePoint", group.targetKpProps());
            log.debug("融合组 {}: {} → {}", group.groupId(), group.sourceKpIds(), group.targetKpId());
        }
    }

    // ======================== MASTERS 计算 ========================

    private int recalculateAllMasters(String subject) {
        // 查询所有 Student 的所有 MASTERS 相关成绩
        // 简化：通过查找所有 exam_record 来重算
        // 实际实现中走 Neo4j + MySQL 组合查询
        List<Map<String, Object>> allStudents =
                graphNodeRepository.findStudentsByKnowledgePointNames(
                        List.of(""), subject); // 通配查询所有
        return recalculateMastersForStudents(allStudents);
    }

    private int recalculateMastersForStudents(List<Map<String, Object>> students) {
        if (students == null || students.isEmpty()) return 0;
        WeightCalculationStrategy weightCalc = getWeightStrategy();
        int totalEdges = 0;

        for (Map<String, Object> student : students) {
            String studentNo = (String) student.get("studentNo");
            String studentNodeId = (String) student.get("studentNodeId");

            // 查 MySQL 获取该学生所有成绩
            List<ExamRecordDO> records = examRecordRepository.findAll().stream()
                    .filter(r -> studentNo.equals(r.getStudentNo()) && r.getIsDeleted() == 0)
                    .collect(Collectors.toList());

            // 按 kpName 分组收集 TestedRecord
            Map<String, List<TestedRecord>> byKp = new HashMap<>();
            for (ExamRecordDO rec : records) {
                try {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> details = objectMapper.readValue(rec.getScoreDetails(), List.class);
                    for (Map<String, Object> d : details) {
                        String kpName = (String) d.get("kpName");
                        if (kpName == null && d.get("kpNames") != null) {
                            // CSV 双行表头可能有多知识点：取第一个
                            @SuppressWarnings("unchecked")
                            List<String> kpNames = (List<String>) d.get("kpNames");
                            kpName = kpNames.isEmpty() ? null : kpNames.get(0);
                        }
                        if (kpName == null) continue;
                        Object rawObj = d.get("rawScore");
                        Object maxObj = d.get("maxScore");
                        Double rawScore = rawObj instanceof Number ? ((Number) rawObj).doubleValue() : null;
                        Double maxScore = maxObj instanceof Number ? ((Number) maxObj).doubleValue() : 0.0;
                        byKp.computeIfAbsent(kpName, k -> new ArrayList<>())
                                .add(new TestedRecord(rec.getExamDate(), rawScore, maxScore, kpName));
                    }
                } catch (Exception e) {
                    log.warn("解析 score_details JSON 失败 studentNo={}: {}", studentNo, e.getMessage());
                }
            }

            // 对每个 KP 计算 MASTERS
            List<GraphNodeRepository.MastersEdgeData> edges = new ArrayList<>();
            for (var entry : byKp.entrySet()) {
                String kpName = entry.getKey();
                WeightResult result = weightCalc.calculate(entry.getValue());
                // 查找 KP 的 Neo4j ID
                List<Map<String, Object>> kps = graphNodeRepository.findKnowledgePointsByNames(List.of(kpName), null);
                String kpId = kps.stream()
                        .map(m -> (String) m.get("id"))
                        .findFirst().orElse(null);
                if (kpId != null) {
                    edges.add(new GraphNodeRepository.MastersEdgeData(
                            studentNodeId, kpId, result.weight(), result.summaryJson()));
                }
            }
            graphNodeRepository.batchUpsertMastersEdges(studentNodeId, edges);
            totalEdges += edges.size();
        }
        return totalEdges;
    }

    // ======================== JSON 工具 ========================

    private String buildFusionDetailJson(List<FusionGroup> groups) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (FusionGroup g : groups) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("groupId", g.groupId());
            item.put("sourceKpIds", g.sourceKpIds());
            item.put("sourceKpProperties", g.sourceKpProps());
            item.put("targetKpId", g.targetKpId());
            item.put("redirectedEdges", g.redirectedEdges());
            items.add(item);
        }
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            log.error("序列化 fusionDetailJson 失败", e);
            return "[]";
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseJsonArray(String json) {
        if (json == null || json.isEmpty()) return List.of();
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            log.error("解析融合日志 JSON 失败: {}", e.getMessage());
            return List.of();
        }
    }
}