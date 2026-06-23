package com.graphnexus.application.ops.snapshot.service.impl;

import com.graphnexus.application.ops.snapshot.model.SnapshotData;
import com.graphnexus.application.ops.snapshot.service.SnapshotService;
import com.graphnexus.infrastructure.mysql.file.repository.ExamRecordRepository;
import com.graphnexus.infrastructure.mysql.file.repository.TextbookRepository;
import com.graphnexus.infrastructure.mysql.ops.entity.OperationType;
import com.graphnexus.infrastructure.mysql.ops.entity.StatsSnapshotDO;
import com.graphnexus.infrastructure.mysql.ops.repository.AuditLogRepository;
import com.graphnexus.infrastructure.mysql.ops.repository.StatsSnapshotRepository;
import com.graphnexus.infrastructure.neo4j.repository.QueryGraphRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 统计快照采集实现。
 * 每日凌晨定时执行，采集全量统计指标并写入 stats_snapshot 表。
 *
 * @author Jay
 * @date 2026/06/23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotServiceImpl implements SnapshotService {

    private final AuditLogRepository auditLogRepository;
    private final TextbookRepository textbookRepository;
    private final ExamRecordRepository examRecordRepository;
    private final StatsSnapshotRepository statsSnapshotRepository;
    private final Neo4jClient neo4jClient;
    private final QueryGraphRepository queryGraphRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Scheduled(cron = "${ops.snapshot.cron:0 0 2 * * ?}")
    public void takeDailySnapshot() {
        LocalDate today = LocalDate.now();
        log.info("开始每日统计快照采集: date={}", today);

        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.atTime(LocalTime.MAX);

        StringBuilder failReasons = new StringBuilder();
        String status = "COMPLETED";

        SnapshotData.UsageData usage = null;
        SnapshotData.DocumentData documents = null;
        SnapshotData.GraphData graph = null;

        // 采集系统使用量
        try {
            usage = collectUsageData(todayStart, todayEnd);
        } catch (Exception e) {
            log.error("快照采集失败 - 系统使用量: {}", e.getMessage(), e);
            failReasons.append("usage:").append(e.getMessage()).append("; ");
            status = "PARTIAL";
        }

        // 采集文档处理量
        try {
            documents = collectDocumentData();
        } catch (Exception e) {
            log.error("快照采集失败 - 文档处理量: {}", e.getMessage(), e);
            failReasons.append("documents:").append(e.getMessage()).append("; ");
            status = "PARTIAL";
        }

        // 采集图谱分布
        try {
            graph = collectGraphData();
        } catch (Exception e) {
            log.error("快照采集失败 - 图谱分布: {}", e.getMessage(), e);
            failReasons.append("graph:").append(e.getMessage()).append("; ");
            status = "PARTIAL";
        }

        if (usage == null && documents == null && graph == null) {
            status = "FAILED";
            log.error("快照采集全部失败: date={}", today);
        }

        try {
            SnapshotData snapshotData = SnapshotData.builder()
                    .usage(usage != null ? usage : SnapshotData.UsageData.builder().build())
                    .documents(documents != null ? documents : SnapshotData.DocumentData.builder().build())
                    .graph(graph != null ? graph : SnapshotData.GraphData.builder().build())
                    .build();

            String json = objectMapper.writeValueAsString(snapshotData);
            StatsSnapshotDO snapshot = StatsSnapshotDO.builder()
                    .snapshotDate(today)
                    .snapshotData(json)
                    .status(status)
                    .failReason(failReasons.length() > 0 ? failReasons.toString() : null)
                    .build();
            statsSnapshotRepository.save(snapshot);
            log.info("每日统计快照采集完成: date={}, status={}", today, status);
        } catch (Exception e) {
            log.error("快照数据保存失败: date={}, {}", today, e.getMessage(), e);
        }
    }

    private SnapshotData.UsageData collectUsageData(LocalDateTime start, LocalDateTime end) {
        long activeUsers = auditLogRepository.countDistinctUserIdByCreateTimeBetween(start, end);
        long loginCount = auditLogRepository.countByOperationTypeAndCreateTimeBetween(OperationType.LOGIN, start, end);
        long uploadCount = auditLogRepository.countByOperationTypeAndCreateTimeBetween(OperationType.DOCUMENT_UPLOAD, start, end);
        long processCount = auditLogRepository.countByOperationTypeAndCreateTimeBetween(OperationType.DOCUMENT_PROCESS, start, end);
        long qaCount = auditLogRepository.countByOperationTypeAndCreateTimeBetween(OperationType.QA_ASK, start, end);

        return SnapshotData.UsageData.builder()
                .activeUsers(activeUsers).loginCount(loginCount)
                .documentUploadCount(uploadCount).documentProcessCount(processCount)
                .qaAskCount(qaCount).build();
    }

    private SnapshotData.DocumentData collectDocumentData() {
        long total = textbookRepository.count();
        long examTotal = examRecordRepository.count();
        List<Object[]> statusRows = textbookRepository.countGroupByStatus();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Object[] row : statusRows) {
            byStatus.put(row[0].toString(), (Long) row[1]);
        }
        List<Object[]> subjectRows = textbookRepository.countGroupBySubject();
        Map<String, Long> bySubject = new LinkedHashMap<>();
        for (Object[] row : subjectRows) {
            bySubject.put(row[0].toString(), (Long) row[1]);
        }
        return SnapshotData.DocumentData.builder().total(total).examTotal(examTotal).byStatus(byStatus).bySubject(bySubject).build();
    }

    @SuppressWarnings("unchecked")
    private SnapshotData.GraphData collectGraphData() {
        Map<String, Long> nodesByType = neo4jClient.query(
                "MATCH (n) RETURN labels(n)[0] AS type, count(n) AS cnt ORDER BY cnt DESC"
        ).fetch().all().stream().collect(Collectors.toMap(
                row -> (String) row.get("type"), row -> (Long) row.get("cnt"),
                (a, b) -> a, LinkedHashMap::new));

        Map<String, Long> edgesByType = neo4jClient.query(
                "MATCH ()-[r]->() RETURN type(r) AS type, count(r) AS cnt ORDER BY cnt DESC"
        ).fetch().all().stream().collect(Collectors.toMap(
                row -> (String) row.get("type"), row -> (Long) row.get("cnt"),
                (a, b) -> a, LinkedHashMap::new));

        Map<String, SnapshotData.SubjectGraphData> bySubject = new LinkedHashMap<>();
        List<String> subjects = queryGraphRepository.findDistinctSubjects();
        for (String subject : subjects) {
            try {
                Map<String, Long> subjNodes = neo4jClient.query(
                        "MATCH (n)-[:BELONGS_TO_SUBJECT]->(:Subject {name: $s}) " +
                        "RETURN labels(n)[0] AS type, count(n) AS cnt ORDER BY cnt DESC"
                ).bindAll(Map.of("s", subject)).fetch().all().stream()
                        .collect(Collectors.toMap(r -> (String) r.get("type"), r -> (Long) r.get("cnt"),
                                (a, b) -> a, LinkedHashMap::new));
                Map<String, Long> subjEdges = neo4jClient.query(
                        "MATCH (n)-[r]-(m) WHERE (n)-[:BELONGS_TO_SUBJECT]->(:Subject {name: $s}) " +
                        "RETURN type(r) AS type, count(r) AS cnt ORDER BY cnt DESC"
                ).bindAll(Map.of("s", subject)).fetch().all().stream()
                        .collect(Collectors.toMap(r -> (String) r.get("type"), r -> (Long) r.get("cnt"),
                                (a, b) -> a, LinkedHashMap::new));
                bySubject.put(subject, SnapshotData.SubjectGraphData.builder().nodes(subjNodes).edges(subjEdges).build());
            } catch (Exception e) {
                log.warn("快照采集 - 学科[{}]图谱统计失败: {}", subject, e.getMessage());
            }
        }
        return SnapshotData.GraphData.builder().nodesByType(nodesByType).edgesByType(edgesByType).bySubject(bySubject).build();
    }
}