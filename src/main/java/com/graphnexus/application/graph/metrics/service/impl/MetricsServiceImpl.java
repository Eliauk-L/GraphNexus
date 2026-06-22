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
import com.graphnexus.infrastructure.neo4j.repository.ConstructionGraphRepository;
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

    /** 指标计算中心节点白名单：仅允许知识点或学生为中心（见 DESIGN D11） */
    private static final String KP_LABEL = NodeType.KNOWLEDGE_POINT.getLabel();
    private static final String STUDENT_LABEL = NodeType.STUDENT.getLabel();
    private static final Set<String> ALLOWED_CENTER_NODES = Set.of(KP_LABEL, STUDENT_LABEL);

    private final GdsAdapter gdsAdapter;
    private final MetricsProperties metricsProperties;
    private final ConstructionGraphRepository constructionGraphRepository;

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
        Set<String> normalizedNodes = validateAndConstrainNodes(nodeTypes);
        Set<String> normalizedEdges = constrainEdgeTypes(normalizedNodes, edgeTypes);
        Set<String> allNodes = expandNodeTypesForProjection(normalizedNodes, normalizedEdges);

        MetricsQuery query = new MetricsQuery(allNodes, normalizedEdges, "pagerank");
        return cache.get(query.toCacheKey(), key -> {
            log.debug("缓存未命中，执行 PageRank 计算（nodeTypes={}, edgeTypes={}）", allNodes, normalizedEdges);
            String graphName = gdsAdapter.projectGraph(allNodes, normalizedEdges);
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
        Set<String> normalizedNodes = validateAndConstrainNodes(nodeTypes);
        Set<String> normalizedEdges = constrainEdgeTypes(normalizedNodes, edgeTypes);
        Set<String> allNodes = expandNodeTypesForProjection(normalizedNodes, normalizedEdges);

        MetricsQuery query = new MetricsQuery(allNodes, normalizedEdges, "degree");
        return cache.get(query.toCacheKey(), key -> {
            log.debug("缓存未命中，执行度中心性计算（nodeTypes={}, edgeTypes={}）", allNodes, normalizedEdges);
            String graphName = gdsAdapter.projectGraph(allNodes, normalizedEdges);
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
    public List<MetricResultBO> queryPageRank(Set<String> nodeTypes, Set<String> edgeTypes, String subjectName) {
        return queryPageRank(nodeTypes, edgeTypes, subjectName, null);
    }

    @Override
    public List<MetricResultBO> queryDegree(Set<String> nodeTypes, Set<String> edgeTypes, String subjectName) {
        return queryDegree(nodeTypes, edgeTypes, subjectName, null);
    }

    @Override
    public List<MetricResultBO> queryPageRank(Set<String> nodeTypes, Set<String> edgeTypes, String subjectName, String documentId) {
        List<MetricResultBO> fullResults = queryPageRank(nodeTypes, edgeTypes);
        return filterByScope(fullResults, subjectName, documentId);
    }

    @Override
    public List<MetricResultBO> queryDegree(Set<String> nodeTypes, Set<String> edgeTypes, String subjectName, String documentId) {
        List<MetricResultBO> fullResults = queryDegree(nodeTypes, edgeTypes);
        return filterByScope(fullResults, subjectName, documentId);
    }

    /**
     * 按学科或文档范围过滤指标结果。
     *
     * <p>subjectName 和 documentId 至少一个非空。同时传时取交集（AND）。
     * 全图 GDS 计算一次，结果按 scope 后置过滤，不同 scope 共享缓存。</p>
     */
    private List<MetricResultBO> filterByScope(List<MetricResultBO> fullResults, String subjectName, String documentId) {
        Set<String> scopeKpIds = null;

        if (subjectName != null && !subjectName.isBlank()) {
            scopeKpIds = constructionGraphRepository.findKpIdsBySubject(subjectName);
        }

        if (documentId != null && !documentId.isBlank()) {
            Set<String> docKpIds = constructionGraphRepository.findKpIdsByDocumentId(documentId);
            if (scopeKpIds == null) {
                scopeKpIds = docKpIds;
            } else {
                scopeKpIds = new java.util.HashSet<>(scopeKpIds);
                scopeKpIds.retainAll(docKpIds); // 交集
            }
        }

        if (scopeKpIds == null || scopeKpIds.isEmpty()) {
            return fullResults;
        }

        final Set<String> finalScope = scopeKpIds;
        return fullResults.stream()
                .filter(r -> finalScope.contains(r.nodeId()))
                .collect(Collectors.toList());
    }

    @Override
    public void clearCache() {
        cache.invalidateAll();
        log.debug("指标缓存已清空（图谱变更触发）");
    }

    /**
     * 校验并约束中心节点类型——指标计算仅允许以知识点或学生为中心。
     *
     * <p>规则（见 DESIGN D11）：</p>
     * <ol>
     *   <li>nodeTypes 必须非空（指标计算必须指定中心）</li>
     *   <li>仅允许 KnowledgePoint / Student，传其他类型 → A0002</li>
     *   <li>Student 单独使用 → A0002（MASTERS 边连接 Student→KP，缺 KP 则无边成空图）</li>
     * </ol>
     */
    private Set<String> validateAndConstrainNodes(Set<String> nodeTypes) {
        if (nodeTypes == null || nodeTypes.isEmpty()) {
            throw new BusinessException(ErrorCode.A0002,
                    "指标计算必须指定中心节点类型，允许值: KnowledgePoint, Student");
        }
        // 先校验类型合法性（大小写归一化）
        List<String> invalid = nodeTypes.stream()
                .filter(t -> NodeType.fromLabel(t) == null)
                .collect(Collectors.toList());
        if (!invalid.isEmpty()) {
            throw new BusinessException(ErrorCode.A0002,
                    String.format("无效的节点类型: %s", invalid));
        }
        // 归一化为枚举 label
        Set<String> normalized = nodeTypes.stream()
                .map(t -> NodeType.fromLabel(t).getLabel())
                .collect(Collectors.toSet());
        // 白名单校验：仅允许 KP / Student
        List<String> notAllowed = normalized.stream()
                .filter(t -> !ALLOWED_CENTER_NODES.contains(t))
                .collect(Collectors.toList());
        if (!notAllowed.isEmpty()) {
            throw new BusinessException(ErrorCode.A0002,
                    String.format("指标计算仅支持以知识点或学生为中心，不支持: %s", notAllowed));
        }
        // Student 单独拒绝（MASTERS 边需 KP 才能形成）
        if (normalized.size() == 1 && normalized.contains(STUDENT_LABEL)) {
            throw new BusinessException(ErrorCode.A0002,
                    "以学生为中心需同时包含 KnowledgePoint（MASTERS 边连接 Student→KP，缺 KP 则成空图）");
        }
        return normalized;
    }

    /**
     * GDS 投影所需的全量节点类型——不仅包含中心节点，还包含边连接的目标节点。
     * 例如 KP 的 CHILD_OF 连到 KnowledgeCategory，GDS 需要 Category 也在投影中。
     */
    private Set<String> expandNodeTypesForProjection(Set<String> centerNodes, Set<String> edgeTypes) {
        Set<String> allNodes = new HashSet<>(centerNodes);
        for (String et : edgeTypes) {
            switch (et) {
                case "CHILD_OF":
                    allNodes.add(NodeType.KNOWLEDGE_CATEGORY.getLabel());
                    break;
                case "BELONGS_TO_SUBJECT":
                    allNodes.add("Subject");
                    break;
                case "ALIGNED_TO":
                    allNodes.add(NodeType.ENTITY.getLabel());
                    break;
                case "MASTERS":
                    allNodes.add(NodeType.STUDENT.getLabel());
                    break;
                case "TESTED":
                    allNodes.add(NodeType.EXAM.getLabel());
                    break;
                default:
                    break;
            }
        }
        return allNodes;
    }

    /**
     * 按中心节点类型收敛边类型——仅保留与中心语义匹配的边。
     *
     * <p>收敛规则：</p>
     * <ul>
     *   <li>含 KnowledgePoint → 允许 PREREQUISITE_OF（KP↔KP 知识结构）</li>
     *   <li>含 Student → 允许 MASTERS（Student→KP 掌握度）</li>
     *   <li>调用方传入的 edgeTypes 与允许集合取交集；交集为空 → A0002</li>
     *   <li>调用方未传 edgeTypes → 使用全部允许边</li>
     * </ul>
     */
    private Set<String> constrainEdgeTypes(Set<String> normalizedNodes, Set<String> edgeTypes) {
        Set<String> allowed = new HashSet<>();
        if (normalizedNodes.contains(KP_LABEL)) {
            // KP 连接多种节点：PREREQUISITE_OF(KP→KP), CHILD_OF(KP→Category),
            // BELONGS_TO_SUBJECT(KP→Subject), ALIGNED_TO(Entity→KP),
            // MASTERS(Student→KP), TESTED(Exam→KP)
            allowed.add(EdgeType.PREREQUISITE_OF.getRelationshipType());
            allowed.add(EdgeType.CHILD_OF.getRelationshipType());
            allowed.add(EdgeType.BELONGS_TO_SUBJECT.getRelationshipType());
            allowed.add(EdgeType.ALIGNED_TO.getRelationshipType());
            allowed.add(EdgeType.MASTERS.getRelationshipType());
            allowed.add(EdgeType.TESTED.getRelationshipType());
        }
        if (normalizedNodes.contains(STUDENT_LABEL)) {
            allowed.add(EdgeType.MASTERS.getRelationshipType());
        }
        if (edgeTypes == null || edgeTypes.isEmpty()) {
            return allowed;
        }
        // 校验边类型合法性 + 归一化
        List<String> invalid = edgeTypes.stream()
                .filter(t -> EdgeType.fromType(t) == null)
                .collect(Collectors.toList());
        if (!invalid.isEmpty()) {
            throw new BusinessException(ErrorCode.A0002,
                    String.format("无效的边类型: %s", invalid));
        }
        Set<String> normalized = edgeTypes.stream()
                .map(t -> EdgeType.fromType(t).getRelationshipType())
                .collect(Collectors.toSet());
        // 取交集
        Set<String> result = normalized.stream()
                .filter(allowed::contains)
                .collect(Collectors.toSet());
        if (result.isEmpty()) {
            throw new BusinessException(ErrorCode.A0002,
                    String.format("边类型 %s 与中心节点 %s 允许的边 %s 不匹配",
                            normalized, normalizedNodes, allowed));
        }
        return result;
    }
}