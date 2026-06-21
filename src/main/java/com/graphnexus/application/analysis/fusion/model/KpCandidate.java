package com.graphnexus.application.analysis.fusion.model;

/**
 * KP 匹配候选 — 从 KnowledgePointNode 提取的匹配判断所需字段。
 *
 * <p>不持有整个 KnowledgePointNode，仅含匹配策略需要的属性，
 * 避免策略层与 Neo4j 节点层耦合。</p>
 *
 * @param name         知识点名称
 * @param subject      学科
 * @param documentId   关联文档 ID（文档抽取 KP 有值，CSV 导入 KP 为 null）
 * @param fusionSource 融合来源标记
 * @author Jay
 * @date 2026/06/15
 */
public record KpCandidate(
        String name,
        String subject,
        String documentId,
        String fusionSource
) {}