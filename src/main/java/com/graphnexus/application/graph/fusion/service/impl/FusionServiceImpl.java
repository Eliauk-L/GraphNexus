package com.graphnexus.application.graph.fusion.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.application.graph.fusion.config.FusionProperties;
import com.graphnexus.application.graph.fusion.model.*;
import com.graphnexus.application.graph.fusion.service.FusionService;
import com.graphnexus.application.graph.fusion.strategy.KpMatchingStrategy;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
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
 * 宽图谱融合编排服务 — 全量/增量融合流程控制。
 *
 * <p>委托 {@link FusionGroupBuilder}、{@link MastersRecalculationService}、
 * {@link FusionRollbackService} 执行具体逻辑。见 DESIGN §2.1-2.3。</p>
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
    private final FusionProperties fusionProperties;
    private final Map<String, KpMatchingStrategy> matchingStrategies;
    private final ObjectMapper objectMapper;

    private final FusionGroupBuilder groupBuilder;
    private final MastersRecalculationService mastersService;
    private final FusionRollbackService rollbackService;

    // ======================== 全量融合 ========================

    @Override
    public FusionExecuteResult fuseFull() {
        checkConcurrency();
        FusionLogDO logEntry = createLogEntry("MANUAL_FULL");
        LocalDateTime startTime = LocalDateTime.now();

        try {
            List<Map<String, Object>> allKps = graphNodeRepository.findAllKnowledgePointsBySubject(null);
            Set<String> subjects = allKps.stream()
                    .map(m -> (String) m.get("subject"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            KpMatchingStrategy matcher = getMatchingStrategy();
            double threshold = fusionProperties.getMatching().getThreshold();
            List<FusionGroup> allGroups = new ArrayList<>();
            int totalMasters = 0;

            for (String subject : subjects) {
                List<Map<String, Object>> kps = allKps.stream()
                        .filter(m -> subject.equals(m.get("subject")))
                        .collect(Collectors.toList());

                List<FusionGroup> groups = groupBuilder.build(kps, subject, matcher, threshold);
                allGroups.addAll(groups);
                groupBuilder.merge(groups);
                totalMasters += mastersService.recalculateAll(subject);
            }

            String detailJson = buildFusionDetailJson(allGroups);
            updateLogCompleted(logEntry, allGroups.size(), totalMasters, detailJson, startTime);

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
            List<Map<String, Object>> affectedKps =
                    graphNodeRepository.findKnowledgePointsByNames(kpNames, subject);
            if (affectedKps.size() < 2) {
                updateLogCompleted(logEntry, 0, 0, "[]", startTime);
                return new FusionExecuteResult(logEntry.getId(), 0, 0);
            }

            KpMatchingStrategy matcher = getMatchingStrategy();
            double threshold = fusionProperties.getMatching().getThreshold();

            List<FusionGroup> groups = groupBuilder.build(affectedKps, subject, matcher, threshold);
            groupBuilder.merge(groups);

            List<Map<String, Object>> affectedStudents =
                    graphNodeRepository.findStudentsByKnowledgePointNames(kpNames, subject);
            int totalMasters = mastersService.recalculate(affectedStudents);

            String detailJson = buildFusionDetailJson(groups);
            updateLogCompleted(logEntry, groups.size(), totalMasters, detailJson, startTime);

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
        return rollbackService.rollback(fusionLogId);
    }

    // ======================== 状态查询 ========================

    @Override
    public FusionStatusResult getStatus() {
        return fusionLogRepository.findTopByOrderByExecutedAtDesc()
                .map(log -> new FusionStatusResult(
                        log.getId(), log.getTriggerType(), log.getStatus(),
                        log.getExecutedAt(), log.getMergedKpGroupCount(),
                        log.getMastersEdgeCount(), Boolean.TRUE.equals(log.getRolledBack()),
                        log.getFusionDetailJson(), log.getMastersSnapshotJson()))
                .orElse(null);
    }

    // ======================== 策略选择 ========================

    private KpMatchingStrategy getMatchingStrategy() {
        String name = fusionProperties.getMatching().getStrategy();
        KpMatchingStrategy strategy = matchingStrategies.get(name);
        if (strategy == null) {
            throw new BusinessException(ErrorCode.B0001,
                    "未找到 KP 匹配策略: " + name + "，可用: " + matchingStrategies.keySet());
        }
        return strategy;
    }

    // ======================== 并发控制 ========================

    private void checkConcurrency() {
        fusionLogRepository.findTopByStatusOrderByExecutedAtDesc("RUNNING")
                .ifPresent(log -> {
                    throw new BusinessException(ErrorCode.A0017,
                            "融合进行中 (fusionLogId=" + log.getId() + ")，请稍后重试");
                });
    }

    // ======================== 日志管理 ========================

    private FusionLogDO createLogEntry(String triggerType) {
        return fusionLogRepository.save(FusionLogDO.builder()
                .triggerType(triggerType).status("RUNNING")
                .mergedKpGroupCount(0).mastersEdgeCount(0)
                .rolledBack(false).executedAt(LocalDateTime.now()).build());
    }

    private void updateLogCompleted(FusionLogDO logEntry, int groupCount, int mastersCount,
                                     String detailJson, LocalDateTime startTime) {
        logEntry.setStatus("COMPLETED");
        logEntry.setMergedKpGroupCount(groupCount);
        logEntry.setMastersEdgeCount(mastersCount);
        logEntry.setFusionDetailJson(detailJson);
        logEntry.setMastersSnapshotJson("[]");
        logEntry.setExecutedAt(startTime);
        fusionLogRepository.save(logEntry);
    }

    private void updateLogFailed(FusionLogDO logEntry) {
        try {
            logEntry.setStatus("COMPLETED");
            fusionLogRepository.save(logEntry);
        } catch (Exception ignored) {
            log.warn("更新融合日志失败状态时出错: {}", ignored.getMessage());
        }
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
}