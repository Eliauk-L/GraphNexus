package com.graphnexus.application.ops.stats.service.impl;

import com.graphnexus.application.ops.stats.model.OpsSummaryResponse;
import com.graphnexus.application.ops.stats.model.OpsTrendResponse;
import com.graphnexus.application.ops.stats.service.OpsStatsService;
import com.graphnexus.infrastructure.mysql.file.repository.ExamRecordRepository;
import com.graphnexus.infrastructure.mysql.file.repository.TextbookRepository;
import com.graphnexus.infrastructure.mysql.ops.entity.OperationType;
import com.graphnexus.infrastructure.mysql.ops.repository.AuditLogRepository;
import com.graphnexus.infrastructure.mysql.ops.repository.StatsSnapshotRepository;
import com.graphnexus.infrastructure.neo4j.repository.QueryGraphRepository;
import com.graphnexus.infrastructure.mysql.ops.entity.StatsSnapshotDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.application.ops.snapshot.model.SnapshotData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 运营统计聚合查询实现。
 * 实时面板查现网（MySQL + Neo4j），历史趋势走快照表。
 *
 * @author Jay
 * @date 2026/06/23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpsStatsServiceImpl implements OpsStatsService {

    private final AuditLogRepository auditLogRepository;
    private final TextbookRepository textbookRepository;
    private final ExamRecordRepository examRecordRepository;
    private final StatsSnapshotRepository statsSnapshotRepository;
    private final QueryGraphRepository queryGraphRepository;
    private final Neo4jClient neo4jClient;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public OpsSummaryResponse getSummary() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);

        return OpsSummaryResponse.builder()
                .usage(buildUsageSummary(todayStart, todayEnd))
                .documents(buildDocumentSummary())
                .graph(buildGraphSummary())
                .build();
    }

    private OpsSummaryResponse.UsageSummary buildUsageSummary(LocalDateTime start, LocalDateTime end) {
        long activeUsers = auditLogRepository.countDistinctUserIdByCreateTimeBetween(start, end);
        long loginCount = auditLogRepository.countByOperationTypeAndCreateTimeBetween(OperationType.LOGIN, start, end);
        long uploadCount = auditLogRepository.countByOperationTypeAndCreateTimeBetween(OperationType.DOCUMENT_UPLOAD, start, end);
        long processCount = auditLogRepository.countByOperationTypeAndCreateTimeBetween(OperationType.DOCUMENT_PROCESS, start, end);
        long qaCount = auditLogRepository.countByOperationTypeAndCreateTimeBetween(OperationType.QA_ASK, start, end);

        List<Object[]> dist = auditLogRepository.countGroupByOperationTypeBetween(start, end);
        List<OpsSummaryResponse.OperationCount> opDist = dist.stream()
                .map(row -> OpsSummaryResponse.OperationCount.builder()
                        .type(row[0].toString())
                        .count((Long) row[1])
                        .build())
                .collect(Collectors.toList());

        return OpsSummaryResponse.UsageSummary.builder()
                .activeUsers(activeUsers)
                .loginCount(loginCount)
                .documentUploadCount(uploadCount)
                .documentProcessCount(processCount)
                .qaAskCount(qaCount)
                .operationDistribution(opDist)
                .build();
    }

    private OpsSummaryResponse.DocumentSummary buildDocumentSummary() {
        long total = textbookRepository.count();
        long examTotal = examRecordRepository.count();
        // 按状态分布
        List<Object[]> statusRows = textbookRepository.countGroupByStatus();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Object[] row : statusRows) {
            byStatus.put(row[0].toString(), (Long) row[1]);
        }
        // 按学科分布
        List<Object[]> subjectRows = textbookRepository.countGroupBySubject();
        Map<String, Long> bySubject = new LinkedHashMap<>();
        for (Object[] row : subjectRows) {
            bySubject.put(row[0].toString(), (Long) row[1]);
        }

        return OpsSummaryResponse.DocumentSummary.builder()
                .total(total)
                .examTotal(examTotal)
                .byStatus(byStatus)
                .bySubject(bySubject)
                .build();
    }

    private OpsSummaryResponse.GraphSummary buildGraphSummary() {
        // 节点按类型计数
        Map<String, Long> nodesByType = queryNodeCountsByType();
        // 边按类型计数
        Map<String, Long> edgesByType = queryEdgeCountsByType();
        // 按学科分布
        Map<String, OpsSummaryResponse.SubjectGraphDistribution> bySubject = queryGraphBySubject();

        return OpsSummaryResponse.GraphSummary.builder()
                .nodesByType(nodesByType)
                .edgesByType(edgesByType)
                .bySubject(bySubject)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> queryNodeCountsByType() {
        try {
            return neo4jClient.query(
                    "MATCH (n) RETURN labels(n)[0] AS type, count(n) AS cnt ORDER BY cnt DESC"
            ).fetch().all().stream()
                    .collect(Collectors.toMap(
                            row -> (String) row.get("type"),
                            row -> (Long) row.get("cnt"),
                            (a, b) -> a, LinkedHashMap::new));
        } catch (Exception e) {
            log.warn("Neo4j 节点类型统计查询失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> queryEdgeCountsByType() {
        try {
            return neo4jClient.query(
                    "MATCH ()-[r]->() RETURN type(r) AS type, count(r) AS cnt ORDER BY cnt DESC"
            ).fetch().all().stream()
                    .collect(Collectors.toMap(
                            row -> (String) row.get("type"),
                            row -> (Long) row.get("cnt"),
                            (a, b) -> a, LinkedHashMap::new));
        } catch (Exception e) {
            log.warn("Neo4j 边类型统计查询失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, OpsSummaryResponse.SubjectGraphDistribution> queryGraphBySubject() {
        Map<String, OpsSummaryResponse.SubjectGraphDistribution> result = new LinkedHashMap<>();
        List<String> subjects = getSubjects();
        for (String subject : subjects) {
            try {
                Map<String, Long> nodes = neo4jClient.query(
                        "MATCH (n)-[:BELONGS_TO_SUBJECT]->(:Subject {name: $subject}) " +
                        "RETURN labels(n)[0] AS type, count(n) AS cnt ORDER BY cnt DESC"
                ).bindAll(Map.of("subject", subject)).fetch().all().stream()
                        .collect(Collectors.toMap(
                                row -> (String) row.get("type"),
                                row -> (Long) row.get("cnt"),
                                (a, b) -> a, LinkedHashMap::new));

                Map<String, Long> edges = neo4jClient.query(
                        "MATCH (n)-[r]-(m) WHERE (n)-[:BELONGS_TO_SUBJECT]->(:Subject {name: $subject}) " +
                        "RETURN type(r) AS type, count(r) AS cnt ORDER BY cnt DESC"
                ).bindAll(Map.of("subject", subject)).fetch().all().stream()
                        .collect(Collectors.toMap(
                                row -> (String) row.get("type"),
                                row -> (Long) row.get("cnt"),
                                (a, b) -> a, LinkedHashMap::new));

                result.put(subject, OpsSummaryResponse.SubjectGraphDistribution.builder()
                        .nodes(nodes).edges(edges).build());
            } catch (Exception e) {
                log.warn("Neo4j 学科[{}]图谱统计查询失败: {}", subject, e.getMessage());
            }
        }
        return result;
    }

    @Override
    public OpsTrendResponse getTrend(String metric, String granularity, int range) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(range);
        List<StatsSnapshotDO> snapshots = statsSnapshotRepository
                .findBySnapshotDateBetweenOrderBySnapshotDateAsc(start, end);

        List<OpsTrendResponse.TrendPoint> points = new ArrayList<>();
        for (StatsSnapshotDO snapshot : snapshots) {
            try {
                SnapshotData data = objectMapper.readValue(snapshot.getSnapshotData(), SnapshotData.class);
                double value = extractMetricValue(data, metric);
                points.add(OpsTrendResponse.TrendPoint.builder()
                        .date(snapshot.getSnapshotDate().toString())
                        .value(value)
                        .build());
            } catch (Exception e) {
                log.warn("快照数据解析失败: date={}, {}", snapshot.getSnapshotDate(), e.getMessage());
            }
        }

        return OpsTrendResponse.builder()
                .metric(metric)
                .granularity(granularity)
                .dataPoints(points)
                .build();
    }

    private double extractMetricValue(SnapshotData data, String metric) {
        switch (metric) {
            case "active_users": return data.getUsage().getActiveUsers();
            case "login_count": return data.getUsage().getLoginCount();
            case "document_upload_count": return data.getUsage().getDocumentUploadCount();
            case "document_process_count": return data.getUsage().getDocumentProcessCount();
            case "qa_ask_count": return data.getUsage().getQaAskCount();
            case "document_total": return data.getDocuments().getTotal();
            case "kp_count":
                return data.getGraph().getNodesByType().getOrDefault("KnowledgePoint", 0L);
            case "entity_count":
                return data.getGraph().getNodesByType().getOrDefault("Entity", 0L);
            default: return 0;
        }
    }

    @Override
    public List<String> getSubjects() {
        return queryGraphRepository.findDistinctSubjects();
    }
}