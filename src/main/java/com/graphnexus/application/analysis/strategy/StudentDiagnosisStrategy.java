package com.graphnexus.application.analysis.strategy;

import com.graphnexus.application.analysis.model.PruningRequest;
import com.graphnexus.application.analysis.model.PrunedSubgraph;
import com.graphnexus.application.analysis.model.PrunedSubgraph.PruningMeta;
import com.graphnexus.infrastructure.mysql.document.ExamRecordDO;
import com.graphnexus.infrastructure.mysql.document.ExamRecordRepository;
import com.graphnexus.infrastructure.neo4j.edge.GraphEdge;
import com.graphnexus.infrastructure.neo4j.edge.MastersEdge;
import com.graphnexus.infrastructure.neo4j.edge.PrerequisiteEdge;
import com.graphnexus.infrastructure.neo4j.node.GraphNode;
import com.graphnexus.infrastructure.neo4j.node.KnowledgePointNode;
import com.graphnexus.infrastructure.neo4j.node.StudentNode;
import com.graphnexus.infrastructure.neo4j.repository.GraphNodeRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 学生诊断剪枝策略 — STUDENT_DIAGNOSIS 意图的 v1 实现。
 *
 * <p>四步剪枝逻辑（见 ADR-010）：</p>
 * <ol>
 *   <li>确认学生存在并获取属性</li>
 *   <li>查找弱掌握知识点（MASTERS 优先，降级走 TESTED 路径）</li>
 *   <li>展开前置依赖链（PREREQUISITE_OF ≤ 2 跳）</li>
 *   <li>补全前置 KP 的掌握度</li>
 * </ol>
 *
 * @author Jay
 * @date 2026/06/17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StudentDiagnosisStrategy implements SubgraphPruningStrategy {

    private final GraphNodeRepository graphNodeRepository;
    private final ExamRecordRepository examRecordRepository;
    private final ObjectMapper objectMapper;

    private static final double DEFAULT_WEAK_THRESHOLD = 0.6;
    private static final int DEFAULT_MAX_HOPS = 2;

    @Override
    public PrunedSubgraph prune(PruningRequest request) {
        if (!"STUDENT_DIAGNOSIS".equals(request.intent())) {
            log.warn("StudentDiagnosisStrategy 收到非预期意图: {}", request.intent());
            return emptyResult("UNSUPPORTED_INTENT");
        }

        String studentNo = request.entityId();
        String subject = request.subject();
        double weakThreshold = getParam(request.params(), "weakThreshold", DEFAULT_WEAK_THRESHOLD);
        int maxHops = (int) getParam(request.params(), "maxHops", (double) DEFAULT_MAX_HOPS);

        // Step 1: 确认学生存在
        var studentOpt = graphNodeRepository.findStudentByNo(studentNo);
        if (studentOpt.isEmpty()) {
            log.warn("Student 不存在: studentNo={}", studentNo);
            return emptyResult("STUDENT_NOT_FOUND");
        }
        var studentRow = studentOpt.get();
        StudentNode studentNode = buildStudentNode(studentRow);

        // Step 2: 查找弱掌握 KP（MASTERS 优先，降级走 TESTED）
        String studentNodeId = (String) studentRow.get("id");
        var mastersRows = graphNodeRepository.findMastersByStudentAndSubject(studentNodeId, subject);
        boolean mastersAvailable = !mastersRows.isEmpty();

        Map<String, Double> kpMasteryMap;      // kpId → mastery weight
        Map<String, String> kpNameMap;         // kpId → kpName
        List<String> weakKpIds;

        if (mastersAvailable) {
            kpMasteryMap = new HashMap<>();
            kpNameMap = new HashMap<>();
            for (var row : mastersRows) {
                String kpId = (String) row.get("kpId");
                String kpName = (String) row.get("kpName");
                double weight = ((Number) row.get("weight")).doubleValue();
                kpMasteryMap.put(kpId, weight);
                kpNameMap.put(kpId, kpName);
            }
            weakKpIds = kpMasteryMap.entrySet().stream()
                    .filter(e -> e.getValue() < weakThreshold)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        } else {
            // MASTERS 降级：查 TESTED 路径 + MySQL exam_record 计算原始得分率
            var testedKps = graphNodeRepository.findTestedKpsByStudentAndSubject(studentNo, subject);
            kpMasteryMap = new HashMap<>();
            kpNameMap = new HashMap<>();
            for (var row : testedKps) {
                String kpId = (String) row.get("kpId");
                String kpName = (String) row.get("kpName");
                double scoreRate = calculateRawScoreRate(studentNo, kpName);
                kpMasteryMap.put(kpId, scoreRate);
                kpNameMap.put(kpId, kpName);
            }
            weakKpIds = kpMasteryMap.entrySet().stream()
                    .filter(e -> e.getValue() < weakThreshold)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        }

        // Step 3: 展开前置依赖链
        List<Map<String, Object>> prereqRows = weakKpIds.isEmpty()
                ? Collections.emptyList()
                : graphNodeRepository.findPrerequisitesUpstream(weakKpIds, maxHops);
        Set<String> preKpIds = new HashSet<>();
        for (var row : prereqRows) {
            String toKpId = (String) row.get("toKpId");
            String toKpName = (String) row.get("toKpName");
            preKpIds.add(toKpId);
            kpNameMap.putIfAbsent(toKpId, toKpName);
        }

        // Step 4: 补全前置 KP 的掌握度
        if (!preKpIds.isEmpty()) {
            List<String> preKpIdList = new ArrayList<>(preKpIds);
            var preMasters = graphNodeRepository.findMastersByStudentAndKpIds(studentNodeId, preKpIdList);
            for (var row : preMasters) {
                String kpId = (String) row.get("kpId");
                double weight = ((Number) row.get("weight")).doubleValue();
                kpMasteryMap.putIfAbsent(kpId, weight);
            }
        }

        // 组装结果
        return buildResult(studentNode, kpMasteryMap, kpNameMap,
                weakKpIds, preKpIds, prereqRows, mastersAvailable,
                weakThreshold, maxHops, subject);
    }

    // ======================== 私有辅助方法 ========================

    /**
     * 降级路径：从 MySQL exam_record 计算原始得分率（简单算术平均，无时间衰减）。
     */
    private double calculateRawScoreRate(String studentNo, String kpName) {
        List<ExamRecordDO> records = examRecordRepository.findByStudentNoAndSubject(studentNo, null);
        // subject 可能为 null（跨学科查询），这里按 kpName 过滤
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
                    // kpNames 可能是用 ; 分隔的多个知识点
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

    private StudentNode buildStudentNode(Map<String, Object> row) {
        return new StudentNode(
                (String) row.get("studentNo"),
                (String) row.get("name"),
                (String) row.get("className"),
                (String) row.get("grade")
        );
    }

    private PrunedSubgraph buildResult(
            StudentNode studentNode,
            Map<String, Double> kpMasteryMap,
            Map<String, String> kpNameMap,
            List<String> weakKpIds,
            Set<String> preKpIds,
            List<Map<String, Object>> prereqRows,
            boolean mastersAvailable,
            double weakThreshold,
            int maxHops,
            String subject) {

        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();
        Set<String> seenNodeIds = new HashSet<>();

        // Student 节点
        nodes.add(studentNode);
        seenNodeIds.add(studentNode.getId());

        // 弱掌握 KP 节点 + MASTERS 边
        for (String kpId : weakKpIds) {
            if (seenNodeIds.add(kpId)) {
                nodes.add(buildKpNode(kpId, kpNameMap.get(kpId), subject));
            }
            Double weight = kpMasteryMap.get(kpId);
            if (weight != null) {
                edges.add(new MastersEdge(studentNode.getId(), kpId));
                // 直接设置 weight（通过基类属性）
                GraphEdge last = edges.get(edges.size() - 1);
                last.setWeight(weight);
            }
        }

        // 前置依赖 KP 节点 + PREREQUISITE_OF 边
        for (String kpId : preKpIds) {
            if (!seenNodeIds.contains(kpId)) {
                if (seenNodeIds.add(kpId)) {
                    nodes.add(buildKpNode(kpId, kpNameMap.get(kpId), subject));
                }
                // 前置 KP 的 MASTERS 边
                Double weight = kpMasteryMap.get(kpId);
                if (weight != null) {
                    edges.add(new MastersEdge(studentNode.getId(), kpId));
                    edges.get(edges.size() - 1).setWeight(weight);
                }
            }
        }
        for (var row : prereqRows) {
            Object toKpIdObj = row.get("toKpId");
            if (toKpIdObj == null) continue;
            String toKpId = toKpIdObj.toString();
            // 确保目标节点在 nodes 中
            if (seenNodeIds.add(toKpId)) {
                nodes.add(buildKpNode(toKpId, kpNameMap.get(toKpId), subject));
            }
            edges.add(new PrerequisiteEdge(
                    (String) row.get("fromKpId"), toKpId, 1.0, ""));
        }

        // 元信息
        PruningMeta meta = new PruningMeta(
                "STUDENT_DIAGNOSIS",
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

    private KnowledgePointNode buildKpNode(String id, String name, String subject) {
        KnowledgePointNode kp = new KnowledgePointNode(name, subject);
        kp.setId(id);
        return kp;
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
}