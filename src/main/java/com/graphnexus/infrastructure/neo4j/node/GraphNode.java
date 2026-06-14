package com.graphnexus.infrastructure.neo4j.node;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.neo4j.core.schema.Id;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 图节点抽象基类 — 所有 Neo4j 节点类型的公共字段容器。
 *
 * <p>本类不标注 {@code @Node}，由子类各自标注对应的 label。
 * 设计决策见 ADR-002。</p>
 *
 * @author Jay
 * @date 2026/06/13
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class GraphNode {

    /**
     * Neo4j 内部 ID（UUID 生成，不作为业务主键）。
     */
    @Id
    private String id;

    /**
     * 节点类型标识，对应 {@link NodeType} 枚举的 label 值。
     * 例：{@code "Entity"}、{@code "KnowledgePoint"}。
     * 写入 Neo4j 后可通过 {@code WHERE n.nodeType = 'Entity'} 过滤。
     */
    private String nodeType;

    /**
     * 关联的源文档 ID（对应 MySQL document 表的 id，或 Neo4j DocumentNode.id）。
     * 所有从同一文档抽取的节点共享此值，用于按文档查询子图。
     */
    private String documentId;

    /**
     * 节点创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 扩展属性容器 — 存放非固定字段（如 LLM 抽取出的额外元数据）。
     * 核心字段必须在子类中显式定义，不应全部塞进此 Map。
     */
    private Map<String, Object> properties;

    /**
     * 子类构造时调用此方法生成 ID 并设创建时间。
     */
    protected GraphNode(String nodeType) {
        this.id = UUID.randomUUID().toString();
        this.nodeType = nodeType;
        this.createdAt = LocalDateTime.now();
        this.properties = new HashMap<>();
    }
}