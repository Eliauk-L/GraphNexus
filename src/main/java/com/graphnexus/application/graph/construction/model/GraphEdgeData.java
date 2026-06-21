package com.graphnexus.application.graph.construction.model;

import java.time.LocalDateTime;

/**
 * 图边数据记录 — L2 层的图边视图，从 L3 的 {@code GraphEdge} 转换而来。
 *
 * @param sourceNodeId 源节点 elementId
 * @param targetNodeId 目标节点 elementId
 * @param edgeType     边类型
 * @param weight       边权重（0~1）
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