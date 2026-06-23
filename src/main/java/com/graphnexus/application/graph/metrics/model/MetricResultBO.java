package com.graphnexus.application.graph.metrics.model;

/**
 * 图指标计算结果 BO —— 不可变 record，供 GdsAdapter → MetricsService → MetricsController 全链路传递。
 *
 * <p>一条 record 对应一个节点的一项指标值。度中心性会为同一个节点产生两条 record
 * （inDegree + outDegree），通过 {@link #metricName} 区分。</p>
 *
 * @param nodeId     Neo4j 节点 ID（如 {@code kp-uuid-xxx}）
 * @param nodeType   Neo4j 节点 label（如 {@code KnowledgePoint}）
 * @param metricName 指标名称（{@code pagerank} / {@code inDegree} / {@code outDegree}）
 * @param metricValue 指标数值
 * @author Jay
 * @date 2026/06/17
 */
public record MetricResultBO(
        String nodeId,
        String nodeType,
        String nodeName,
        String subject,
        String className,
        String metricName,
        Double metricValue
) {
}