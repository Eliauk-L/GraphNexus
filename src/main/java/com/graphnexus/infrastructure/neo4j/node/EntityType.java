package com.graphnexus.infrastructure.neo4j.node;

import lombok.Getter;

/**
 * 实体类型枚举 — EntityNode 的分类标签。
 *
 * <p>LLM 抽取结果中的 entityType 必须为此枚举中的值。
 * 新增实体类型只需在此加一行，同时在 LLM Prompt 中注册。</p>
 *
 * @author Jay
 * @date 2026/06/13
 */
@Getter
public enum EntityType {

    /** 概念定义 */
    DEFINITION("DEFINITION", "概念定义"),

    /** 数学公式/表达式 */
    FORMULA("FORMULA", "公式"),

    /** 概念/性质 */
    CONCEPT("CONCEPT", "概念"),

    /** 例题/习题 */
    EXAMPLE("EXAMPLE", "例题"),

    /** 解题过程/方法 */
    SOLUTION("SOLUTION", "解法");

    /** 枚举值字符串（与 LLM 输出的 JSON 中一致） */
    private final String value;

    /** 中文显示名 */
    private final String displayName;

    EntityType(String value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

    /**
     * 根据字符串查找对应的实体类型。
     */
    public static EntityType fromValue(String value) {
        for (EntityType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }
}