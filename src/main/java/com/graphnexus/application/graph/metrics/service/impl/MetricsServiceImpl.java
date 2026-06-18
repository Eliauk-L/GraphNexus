package com.graphnexus.application.graph.metrics.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.graphnexus.infrastructure.neo4j.gds.config.MetricsProperties;
import com.graphnexus.application.graph.metrics.model.MetricResultBO;
import com.graphnexus.application.graph.metrics.model.MetricsQuery;
import com.graphnexus.application.graph.metrics.service.MetricsService;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.neo4j.edge.EdgeType;
import com.graphnexus.infrastructure.neo4j.gds.GdsAdapter;
import com.graphnexus.infrastructure.neo4j.gds.model.GdsResult;
import com.graphnexus.infrastructure.neo4j.node.NodeType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 图指标计算服务实现 —— 参数校验 + Caffeine 本地缓存 + GDS 调用编排。
 *
 * <p>缓存语义：{@link Cache#get(Object, java.util.function.Function)} 确保同 key
 * 仅触发一次 GDS 计算（并发安全）。图谱变更后 {@link #clearCache()} 全量失效。</p>
 *
 * @author Jay
 * @date 2026/06/17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsServiceImpl implements MetricsService {

    private final GdsAdapter gdsAdapter;
    private final MetricsProperties metricsProperties;

    private Cache<String, List<MetricResultBO>> cache;

    @PostConstruct
    void initCache() {
        MetricsProperties.Cache cacheProps = metricsProperties.getCache();
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(cacheProps.ttlMinutes()))
                .maximumSize(cacheProps.maxSize())
                .recordStats()
                .build();
        log.info("指标缓存初始化完成：TTL={}min, maxSize={}", cacheProps.ttlMinutes(), cacheProps.maxSize());
    }

    @Override
    public List<MetricResultBO> queryPageRank(Set<String> nodeTypes, Set<String> edgeTypes) {
        validateParams(nodeTypes, edgeTypes);
        Set<String> normalizedNodes = normalizeNodeTypes(nodeTypes);
        Set<String> normalizedEdges = normalizeEdgeTypes(edgeTypes);

        MetricsQuery query = new MetricsQuery(normalizedNodes, normalizedEdges, "pagerank");
        return cache.get(query.toCacheKey(), key -> {
            log.debug("缓存未命中，执行 PageRank 计算（nodeTypes={}, edgeTypes={}）", normalizedNodes, normalizedEdges);
            String graphName = gdsAdapter.projectGraph(normalizedNodes, normalizedEdges);
            try {
                return gdsAdapter.runPageRank(graphName).stream()
                        .map(r -> new MetricResultBO(r.nodeId(), r.nodeType(), "pagerank", r.score()))
                        .sorted(Comparator.comparingDouble(MetricResultBO::metricValue).reversed())
                        .collect(Collectors.toList());
            } finally {
                gdsAdapter.dropGraph(graphName);
            }
        });
    }

    @Override
    public List<MetricResultBO> queryDegree(Set<String> nodeTypes, Set<String> edgeTypes) {
        validateParams(nodeTypes, edgeTypes);
        Set<String> normalizedNodes = normalizeNodeTypes(nodeTypes);
        Set<String> normalizedEdges = normalizeEdgeTypes(edgeTypes);

        MetricsQuery query = new MetricsQuery(normalizedNodes, normalizedEdges, "degree");
        return cache.get(query.toCacheKey(), key -> {
            log.debug("缓存未命中，执行度中心性计算（nodeTypes={}, edgeTypes={}）", normalizedNodes, normalizedEdges);
            String graphName = gdsAdapter.projectGraph(normalizedNodes, normalizedEdges);
            try {
                List<MetricResultBO> results = new ArrayList<>();
                results.addAll(gdsAdapter.runDegreeStream(graphName, "NATURAL").stream()
                        .map(r -> new MetricResultBO(r.nodeId(), r.nodeType(), "outDegree", r.score()))
                        .collect(Collectors.toList()));
                results.addAll(gdsAdapter.runDegreeStream(graphName, "REVERSE").stream()
                        .map(r -> new MetricResultBO(r.nodeId(), r.nodeType(), "inDegree", r.score()))
                        .collect(Collectors.toList()));
                return results;
            } finally {
                gdsAdapter.dropGraph(graphName);
            }
        });
    }

    @Override
    public void clearCache() {
        cache.invalidateAll();
        log.debug("指标缓存已清空（图谱变更触发）");
    }

    /**
     * 校验节点类型和边类型的合法性。空集合跳过校验（全图默认）。
     */
    private void validateParams(Set<String> nodeTypes, Set<String> edgeTypes) {
        if (nodeTypes != null && !nodeTypes.isEmpty()) {
            List<String> invalid = nodeTypes.stream()
                    .filter(t -> NodeType.fromLabel(t) == null)
                    .collect(Collectors.toList());
            if (!invalid.isEmpty()) {
                String validValues = Arrays.stream(NodeType.values())
                        .map(NodeType::getLabel)
                        .collect(Collectors.joining(", "));
                throw new BusinessException(ErrorCode.A0002,
                        String.format("无效的节点类型: %s，有效值: %s", invalid, validValues));
            }
        }
        if (edgeTypes != null && !edgeTypes.isEmpty()) {
            List<String> invalid = edgeTypes.stream()
                    .filter(t -> EdgeType.fromType(t) == null)
                    .collect(Collectors.toList());
            if (!invalid.isEmpty()) {
                String validValues = Arrays.stream(EdgeType.values())
                        .map(EdgeType::getRelationshipType)
                        .collect(Collectors.joining(", "));
                throw new BusinessException(ErrorCode.A0002,
                        String.format("无效的边类型: %s，有效值: %s", invalid, validValues));
            }
        }
    }

    /**
     * 将用户输入的节点类型归一化为枚举规范 label（Neo4j label 大小写敏感）。
     */
    private Set<String> normalizeNodeTypes(Set<String> nodeTypes) {
        if (nodeTypes == null || nodeTypes.isEmpty()) return Collections.emptySet();
        return nodeTypes.stream()
                .map(t -> NodeType.fromLabel(t).getLabel())
                .collect(Collectors.toSet());
    }

    /**
     * 将用户输入的边类型归一化为枚举规范 relationshipType。
     */
    private Set<String> normalizeEdgeTypes(Set<String> edgeTypes) {
        if (edgeTypes == null || edgeTypes.isEmpty()) return Collections.emptySet();
        return edgeTypes.stream()
                .map(t -> EdgeType.fromType(t).getRelationshipType())
                .collect(Collectors.toSet());
    }
}