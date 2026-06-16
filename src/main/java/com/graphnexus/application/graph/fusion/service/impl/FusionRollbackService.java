package com.graphnexus.application.graph.fusion.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.application.graph.fusion.model.FusionRollbackResult;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.mysql.fusion.FusionLogDO;
import com.graphnexus.infrastructure.mysql.fusion.FusionLogRepository;
import com.graphnexus.infrastructure.neo4j.repository.GraphNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 融合回滚服务 — 基于 fusion_log JSON 快照逆向恢复 Neo4j 图状态。
 *
 * <p>从 {@link FusionServiceImpl} 中提取，职责单一：读快照 → 逆向恢复 → 标记已回滚。</p>
 *
 * @author Jay
 * @date 2026/06/16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FusionRollbackService {

    private final FusionLogRepository fusionLogRepository;
    private final GraphNodeRepository graphNodeRepository;
    private final ObjectMapper objectMapper;

    /**
     * 基于日志回滚指定融合操作。
     *
     * @param fusionLogId 融合日志 ID
     * @return 回滚结果（恢复 KP 数 + 恢复边数）
     */
    public FusionRollbackResult rollback(Long fusionLogId) {
        FusionLogDO logEntry = fusionLogRepository.findById(fusionLogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.A0016, "融合日志不存在: " + fusionLogId));

        if (Boolean.TRUE.equals(logEntry.getRolledBack())) {
            log.info("融合日志 {} 已回滚，幂等返回", fusionLogId);
            return new FusionRollbackResult(fusionLogId, 0, 0);
        }

        try {
            List<Map<String, Object>> fusionDetails = parseJsonArray(logEntry.getFusionDetailJson());
            List<Map<String, Object>> mastersSnapshots = parseJsonArray(logEntry.getMastersSnapshotJson());

            int restoredKpCount = restoreKnowledgePoints(fusionDetails);
            int restoredEdgeCount = countRestoredEdges(fusionDetails);
            restoreMastersWeights(mastersSnapshots);

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

    // ======================== 逆向恢复步骤 ========================

    private int restoreKnowledgePoints(List<Map<String, Object>> fusionDetails) {
        int restoredKpCount = 0;
        for (Map<String, Object> group : fusionDetails) {
            String targetKpId = (String) group.get("targetKpId");
            // 删除规范 KP
            graphNodeRepository.deleteKnowledgePoints(List.of(targetKpId));

            // 重建源 KP
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sourceKpPropsList =
                    (List<Map<String, Object>>) group.get("sourceKpProperties");
            if (sourceKpPropsList != null) {
                for (Map<String, Object> props : sourceKpPropsList) {
                    graphNodeRepository.createNodeWithProperties("KnowledgePoint", props);
                    restoredKpCount++;
                }
            }
        }
        return restoredKpCount;
    }

    private int countRestoredEdges(List<Map<String, Object>> fusionDetails) {
        int count = 0;
        for (Map<String, Object> group : fusionDetails) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> edges = (List<Map<String, Object>>) group.get("redirectedEdges");
            if (edges != null) count += edges.size();
        }
        return count;
    }

    private void restoreMastersWeights(List<Map<String, Object>> mastersSnapshots) {
        for (Map<String, Object> snap : mastersSnapshots) {
            String studentNo = (String) snap.get("studentNo");
            String kpName = (String) snap.get("kpName");
            Object oldWeightObj = snap.get("oldWeight");

            String studentNodeId = findStudentNodeId(studentNo, kpName);
            if (studentNodeId == null) continue;

            if (oldWeightObj == null) {
                graphNodeRepository.deleteMastersEdge(studentNodeId, kpName);
            } else {
                double oldWeight = ((Number) oldWeightObj).doubleValue();
                String oldDesc = (String) snap.getOrDefault("oldDescription", "");
                graphNodeRepository.updateMastersWeight(studentNodeId, kpName, oldWeight, oldDesc);
            }
        }
    }

    private String findStudentNodeId(String studentNo, String kpName) {
        List<Map<String, Object>> students =
                graphNodeRepository.findStudentsByKnowledgePointNames(List.of(kpName), null);
        return students.stream()
                .filter(s -> studentNo.equals(s.get("studentNo")))
                .map(s -> (String) s.get("studentNodeId"))
                .findFirst().orElse(null);
    }

    // ======================== JSON 工具 ========================

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