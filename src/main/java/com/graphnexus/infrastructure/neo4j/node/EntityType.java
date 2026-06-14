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

    /** 概念定义 — 如"二次函数是指形如 y=ax²+bx+c（a≠0）的函数" */
    DEFINITION("DEFINITION", "概念定义", "教科书中对术语、概念的正式定义语句"),

    /** 数学公式/表达式 — 如"y=ax²+bx+c"、"x=-b/(2a)" */
    FORMULA("FORMULA", "公式", "数学公式或表达式，使用 LaTeX 表示"),

    /** 概念/性质 — 如"对称轴"、"开口方向"、"判别式 Δ" */
    CONCEPT("CONCEPT", "概念", "学科概念或性质描述，非定义/公式类的知识片段"),

    /** 例题/习题 — 含题目文本 */
    EXAMPLE("EXAMPLE", "例题", "例题或习题，包含题目文本，通常以'已知…求…'形式出现"),

    /** 解题过程/方法 — 如"配方法"、"因式分解法" */
    SOLUTION("SOLUTION", "解法", "解题方法、技巧或详细解答过程");

    /** 枚举值字符串（与 LLM 输出的 JSON 中一致） */
    private final String value;

    /** 中文显示名 */
    private final String displayName;

    /** 类型描述 — 说明该实体类型包含什么内容，与 LLM Prompt 中的实体类型定义对应 */
    private final String description;

    EntityType(String value, String displayName, String description) {
        this.value = value;
        this.displayName = displayName;
        this.description = description;
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