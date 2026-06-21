package com.graphnexus.application.graph.construction.extract.registry;

import lombok.Getter;

/**
 * LLM 可抽取的实体间关系类型枚举 — 抽取层关系契约（D3 / ADR-023）。
 *
 * <p>与 {@link com.graphnexus.infrastructure.neo4j.edge.EdgeType} 分工：
 * EdgeType 是 Neo4j 全量边类型注册；本枚举是 LLM 可抽取关系子集契约，
 * 驱动 prompt 关系段 + validator 合法集 + {@link ExtractionEdgeFactory} 路由键。</p>
 *
 * <p>新增关系类型只需在此加一行 + 注册 ExtractionEdgeFactory，不改 switch（AC-3）。</p>
 *
 * @author Jay
 * @date 2026/06/21
 */
@Getter
public enum EntityRelationType {

    /** 推导关系（A 可推导出 B） */
    DERIVES("DERIVES", "推导", "推导关系（A 可推导出 B）"),

    /** 包含关系（A 概念包含 B 子概念） */
    CONTAINS("CONTAINS", "包含", "包含关系（A 概念包含 B 子概念）"),

    /** 引用关系（A 引用/使用了 B） */
    REFERENCES("REFERENCES", "引用", "引用关系（A 引用/使用了 B）");

    /** 枚举值字符串（与 LLM 输出 JSON 中一致） */
    private final String value;

    /** 中文显示名 */
    private final String displayName;

    /** 类型描述 — 与 LLM Prompt 中关系类型定义对应 */
    private final String description;

    EntityRelationType(String value, String displayName, String description) {
        this.value = value;
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * 根据字符串查找对应的关系类型（大小写不敏感）。
     */
    public static EntityRelationType fromValue(String value) {
        for (EntityRelationType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }
}
