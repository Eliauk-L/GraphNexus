package com.graphnexus.application.analysis.fusion.model;

import java.util.List;
import java.util.Map;

/**
 * 融合组 — 一组应合并为同一个规范节点的 KP 候选。
 *
 * @param groupId          融合组编号
 * @param sourceKpIds      源 KP 的 Neo4j 节点 ID 列表
 * @param sourceKpProps    源 KP 的完整属性（回滚重建用）
 * @param targetKpId       规范 KP 的 Neo4j 节点 ID（选主结果）
 * @param targetKpProps    规范 KP 的属性
 * @param redirectedEdges  被重定向的边清单
 * @author Jay
 * @date 2026/06/15
 */
public record FusionGroup(
        int groupId,
        List<String> sourceKpIds,
        List<Map<String, Object>> sourceKpProps,
        String targetKpId,
        Map<String, Object> targetKpProps,
        List<EdgeRedirect> redirectedEdges
) {
    /** 边重定向记录 */
    public record EdgeRedirect(String type, String fromNodeId, String toNodeId, String direction) {}
}