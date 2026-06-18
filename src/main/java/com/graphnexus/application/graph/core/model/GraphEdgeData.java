package com.graphnexus.application.graph.core.model;

import java.time.LocalDateTime;

/**
 * 图边数据记录 — L2 层的图边视图，从 L3 的 {@code GraphEdge} 转换而来。
 *
 * <p>用于 BO 和 VO 层传递边数据，避免 L1 直接依赖 L3 的 Neo4j 实体类型。</p>
 *
 * @param sourceNodeId 源节点 elementId
 * @param targetNodeId 目标节点 elementId
 * @param edgeType     边类型（如 "PREREQUISITE_OF", "MASTERS"）
 * @param weight       边权重（0~1），默认 1.0
 * @param createdAt    边创建时间
 * @author Jay
 * @date 2026/06/18
 */
public record GraphEdgeData(
        String sourceNodeId,
        String targetNodeId,
        String edgeType,
        Double weight,
        LocalDateTime createdAt
) {}