package com.graphnexus.application.query.model;

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

    /** 学生薄弱点诊断：分析某学生在指定学科的弱掌握知识点及根因 */
    STUDENT_DIAGNOSIS,

    // ====== v2 预留 ======

    /** @deprecated v2 知识点班级掌握度分析 */
    // KP_ANALYSIS,

    /** @deprecated v2 班级整体概览 */
    // CLASS_OVERVIEW,

    /** @deprecated v2 前置依赖链追溯 */
    // PREREQUISITE_CHAIN,

    /** @deprecated v2 通用查询（LLM 自主判断剪枝路径） */
    // GENERAL
}