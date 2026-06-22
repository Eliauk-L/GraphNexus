package com.graphnexus.application.analysis.fusion.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.application.analysis.fusion.config.FusionProperties;
import com.graphnexus.application.analysis.fusion.model.*;
import com.graphnexus.application.analysis.fusion.service.FusionService;
import com.graphnexus.application.analysis.fusion.strategy.KpMatchingStrategy;
import com.graphnexus.common.event.GraphChangedEvent;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.mysql.fusion.entity.FusionLogDO;
import com.graphnexus.infrastructure.mysql.fusion.repository.FusionLogRepository;
import com.graphnexus.infrastructure.neo4j.repository.FusionGraphRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

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

    private final FusionGraphRepository fusionGraphRepository;
    private final FusionLogRepository fusionLogRepository;
    private final FusionProperties fusionProperties;
    private final Map<String, KpMatchingStrategy> matchingStrategies;
    private final ObjectMapper objectMapper;
    private final Neo4jTransactionManager neo4jTransactionManager;

    private final FusionGroupBuilder groupBuilder;
    private final MastersRecalculationService mastersService;
    private final FusionRollbackService rollbackService;
    private final ApplicationEventPublisher eventPublisher;

    // ======================== 全量融合 ========================

    @Override
    public FusionExecuteResult fuseFull() {
        checkConcurrency();
        FusionLogDO logEntry = createLogEntry("MANUAL_FULL");
        LocalDateTime startTime = LocalDateTime.now();

        try {
            // ① 预计算阶段（事务外，纯内存计算）：查询全部 KP → 按 subject 构建融合组
            List<Map<String, Object>> allKps = fusionGraphRepository.findAllKnowledgePoints();
            Set<String> subjects = allKps.stream()
                    .map(m -> (String) m.get("subject"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            KpMatchingStrategy matcher = getMatchingStrategy();
            double threshold = fusionProperties.getMatching().getThreshold();
            Map<String, List<FusionGroup>> groupsBySubject = new LinkedHashMap<>();

            for (String subject : subjects) {
                List<Map<String, Object>> kps = allKps.stream()
                        .filter(m -> subject.equals(m.get("subject")))
                        .collect(Collectors.toList());
                List<FusionGroup> groups = groupBuilder.build(kps, subject, matcher, threshold);
                if (!groups.isEmpty()) {
                    groupsBySubject.put(subject, groups);
                }
            }

            // ② Neo4j 事务内：执行全部 merge + MASTERS 重算（见 ADR-020）
            TransactionTemplate txTemplate = new TransactionTemplate(neo4jTransactionManager);
            int totalMasters = txTemplate.execute(status -> {
                int mastersCount = 0;
                for (var entry : groupsBySubject.entrySet()) {
                    groupBuilder.merge(entry.getValue());
                    mastersCount += mastersService.recalculateAll(entry.getKey());
                }
                return mastersCount;
            });

            // ③ 事务成功后：记录 fusion log
            List<FusionGroup> allGroups = groupsBySubject.values().stream()
                    .flatMap(List::stream).collect(Collectors.toList());
            String detailJson = buildFusionDetailJson(allGroups);
            updateLogCompleted(logEntry, allGroups.size(), totalMasters, detailJson, startTime);

            log.info("全量融合完成: {} 组 KP 合并, {} 条 MASTERS 边, fusionLogId={}",
                    allGroups.size(), totalMasters, logEntry.getId());

            eventPublisher.publishEvent(new GraphChangedEvent(this));
            return new FusionExecuteResult(logEntry.getId(), allGroups.size(), totalMasters);

        } catch (BusinessException e) {
            updateLogFailed(logEntry);
            throw e;
        } catch (Exception e) {
            updateLogFailed(logEntry);
            log.error("全量融合失败（Neo4j 事务已回滚）: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.B0001, "融合操作失败: " + e.getMessage());
        }
    }

    // ======================== 增量融合 ========================

    @Override
    public FusionExecuteResult fuseIncremental(List<String> kpNames, String subject) {
        if (kpNames == null || kpNames.isEmpty()) {
            return new FusionExecuteResult(0L, 0, 0);
        }

        checkConcurrency();
        FusionLogDO logEntry = createLogEntry("AUTO_INCREMENTAL");
        LocalDateTime startTime = LocalDateTime.now();

        try {
            // ① 预计算阶段（事务外）
            List<Map<String, Object>> affectedKps =
                    fusionGraphRepository.findKnowledgePointsByNamesAndSubject(kpNames, subject);
            if (affectedKps.size() < 2) {
                updateLogCompleted(logEntry, 0, 0, "[]", startTime);
                return new FusionExecuteResult(logEntry.getId(), 0, 0);
            }

            KpMatchingStrategy matcher = getMatchingStrategy();
            double threshold = fusionProperties.getMatching().getThreshold();
            List<FusionGroup> groups = groupBuilder.build(affectedKps, subject, matcher, threshold);
            List<Map<String, Object>> affectedStudents =
                    fusionGraphRepository.findStudentsByKpNamesAndSubject(kpNames, subject);

            // ② Neo4j 事务内：执行 merge + MASTERS 重算（见 ADR-020）
            TransactionTemplate txTemplate = new TransactionTemplate(neo4jTransactionManager);
            int totalMasters = txTemplate.execute(status -> {
                groupBuilder.merge(groups);
                return mastersService.recalculate(affectedStudents, subject);
            });

            String detailJson = buildFusionDetailJson(groups);
            updateLogCompleted(logEntry, groups.size(), totalMasters, detailJson, startTime);

            // 图谱变更事件 — 触发指标缓存失效（见 ADR-013 §4）
            eventPublisher.publishEvent(new GraphChangedEvent(this));

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
            logEntry.setStatus("FAILED");
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