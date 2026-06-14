package com.graphnexus.infrastructure.neo4j.edge;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 图关系边抽象基类 — 所有 Neo4j 边类型的公共字段容器。
 *
 * <p>本类不标注 {@code @RelationshipProperties}，由子类各自标注。
 * 设计决策见 ADR-002。</p>
 *
 * @author Jay
 * @date 2026/06/13
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class GraphEdge {

    /**
     * 源节点 ID（对应 GraphNode.id）。
     */
    private String sourceNodeId;

    /**
     * 目标节点 ID（对应 GraphNode.id）。
     */
    private String targetNodeId;

    /**
     * 关系类型字符串，对应 {@link EdgeType} 枚举的 relationshipType 值。
     * 例：{@code "EXTRACTS"}、{@code "PREREQUISITE_OF"}。
     */
    private String edgeType;

    /**
     * 关系创建时间 — 便于后续事件图谱中记录事件发生时间。
     */
    private LocalDateTime createdAt;

    /**
     * 关系权重（0~1，默认 1.0）— 为后续事件边（如 MasteryEdge）预留。
     * 可用于表示掌握度、置信度、相关性等强度指标。
     */
    private Double weight;

    /**
     * 关系描述 — 说明此边存在的原因或含义，便于后续扩展（如事件边、推理边）。
     */
    private String description;

    /**
     * 扩展属性容器。
     */
    private Map<String, Object> properties;

    /**
     * 子类构造时调用此方法设创建时间和默认权重。
     */
    protected GraphEdge(String edgeType) {
        this.edgeType = edgeType;
        this.createdAt = LocalDateTime.now();
        this.weight = 1.0;
        this.properties = new HashMap<>();
    }
}