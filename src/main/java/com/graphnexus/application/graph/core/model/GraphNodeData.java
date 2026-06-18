package com.graphnexus.application.graph.core.model;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 图节点数据记录 — L2 层的图节点视图，从 L3 的 {@code GraphNode} 转换而来。
 *
 * <p>用于 BO 和 VO 层传递节点数据，避免 L1 直接依赖 L3 的 Neo4j 实体类型。</p>
 *
 * @param id         节点 Neo4j elementId
 * @param nodeType   节点类型标签（如 "KnowledgePoint", "Student"）
 * @param documentId 关联文档 ID（EntityNode 专属，其他节点为 null）
 * @param createdAt  节点创建时间
 * @param properties 节点属性 Map（供序列化/前端渲染）
 * @author Jay
 * @date 2026/06/18
 */
public record GraphNodeData(
        String id,
        String nodeType,
        String documentId,
        LocalDateTime createdAt,
        Map<String, Object> properties
) {}