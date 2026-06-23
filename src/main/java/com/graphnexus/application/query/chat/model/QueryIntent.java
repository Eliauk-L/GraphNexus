package com.graphnexus.application.query.chat.model;

/**
 * 智能问答意图枚举 — 根据用户自然语言问题判定的查询意图类型。
 *
 * <p>v1 仅支持 {@link #STUDENT_DIAGNOSIS}（学生薄弱点诊断）。
 * 新增意图只需在此枚举加一行 + 实现对应的 {@code SubgraphPruningStrategy}。
 * {@code GENERAL} 兜底意图预留 v2。</p>
 *
 * @author Jay
 * @date 2026/06/17
 */
public enum QueryIntent {

    /**
     * 学生薄弱点诊断：分析某学生在指定学科上的弱掌握知识点，
     * 沿 PREREQUISITE_OF 链追溯根因，生成 Markdown 格式的诊断报告与学习建议。
     *
     * <p>剪枝策略：Student → MASTERS(weight &lt; 阈值) → KnowledgePoint → PREREQUISITE_OF(&le;2跳)</p>
     */
    STUDENT_DIAGNOSIS(
            "学生薄弱点诊断",
            "分析学生在指定学科上的薄弱知识点，追溯前置依赖根因，生成学习建议"
    ),
    CLASS_WEAKNESS_OVERVIEW(
            "班级薄弱概览",
            "聚合全班学生在指定学科上的 MASTERS 数据，统计薄弱知识点排行，分析共性根因"
    );

    // ====== v2 预留 ======
    // KP_ANALYSIS("知识点分析", "分析某知识点的班级整体掌握度分布与教学风险"),
    // PREREQUISITE_CHAIN("依赖链追溯", "沿 PREREQUISITE_OF 双向遍历，展示完整前置依赖链路"),
    // GENERAL("通用查询", "LLM 自主判断剪枝路径，适用于无法归类的自由提问");

    private final String displayName;
    private final String description;

    QueryIntent(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * 意图的简短中文名称（如"学生薄弱点诊断"），用于日志、API 响应等。
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 意图的详细描述，说明该意图分析什么、如何分析。
     */
    public String getDescription() {
        return description;
    }
}