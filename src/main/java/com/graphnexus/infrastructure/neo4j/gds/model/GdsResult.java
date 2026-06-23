package com.graphnexus.infrastructure.neo4j.gds.model;

/**
 * GDS 计算结果记录 — L3 层的原始计算结果。
 *
 * <p>由 {@code GdsAdapter} 返回，L2 {@code MetricsService} 负责转换为 {@code MetricResultBO}。
 * 避免 L3 反向依赖 L2 的业务对象类型。</p>
 *
 * @param nodeId   Neo4j 节点 elementId
 * @param nodeType 节点标签（如 "KnowledgePoint"）
 * @param score    算法得分（PageRank 值或度中心性值）
 * @author Jay
 * @date 2026/06/18
 */
public record GdsResult(
        String nodeId,
        String nodeType,
        String nodeName,
        String subject,
        String className,
        double score
) {}