package com.graphnexus.application.graph.construction.model;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 图节点数据记录 — L2 层的图节点视图，从 L3 的 {@code GraphNode} 转换而来。
 *
 * @param id         节点 Neo4j elementId
 * @param nodeType   节点类型标签
 * @param documentId 关联文档 ID
 * @param createdAt  节点创建时间
 * @param properties 节点属性 Map
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