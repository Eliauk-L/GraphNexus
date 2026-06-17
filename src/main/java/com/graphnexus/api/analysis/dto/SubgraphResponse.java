package com.graphnexus.api.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * 剪枝子图响应 VO — 包含节点、边及剪枝元信息，供前端图可视化渲染。
 *
 * @author Jay
 * @date 2026/06/17
 */
@Schema(description = "剪枝子图响应视图")
public record SubgraphResponse(
        @Schema(description = "对应的问答任务 ID")
        String taskId,

        @Schema(description = "数据状态", example = "OK")
        String status,

        @Schema(description = "子图节点列表")
        List<NodeVO> nodes,

        @Schema(description = "子图边列表")
        List<EdgeVO> edges,

        @Schema(description = "剪枝元信息")
        PruningMetaVO pruningMeta
) {
    @Schema(description = "子图节点视图")
    public record NodeVO(
            @Schema(description = "节点 Neo4j elementId") String id,
            @Schema(description = "节点类型标签", example = "KnowledgePoint") String nodeType,
            @Schema(description = "节点属性键值对", example = "{\"name\":\"二次函数顶点坐标\",\"weight\":0.42}")
            Map<String, Object> properties
    ) {}

    @Schema(description = "子图边视图")
    public record EdgeVO(
            @Schema(description = "源节点 elementId") String sourceNodeId,
            @Schema(description = "目标节点 elementId") String targetNodeId,
            @Schema(description = "边类型", example = "PREREQUISITE_OF") String edgeType,
            @Schema(description = "边权重", example = "0.85") Double weight
    ) {}

    @Schema(description = "剪枝元信息")
    public record PruningMetaVO(
            @Schema(description = "剪枝策略名称", example = "StudentDiagnosisStrategy") String strategy,
            @Schema(description = "MASTERS 融合数据是否可用", example = "true") boolean mastersAvailable,
            @Schema(description = "薄弱点掌握度阈值（低于此值视为薄弱）", example = "0.6") double weakThreshold,
            @Schema(description = "PREREQUISITE_OF 最大跳数", example = "2") int maxHops,
            @Schema(description = "剪枝后子图总节点数", example = "15") int totalNodes,
            @Schema(description = "剪枝后子图总边数", example = "22") int totalEdges,
            @Schema(description = "是否因 token 超限被截断", example = "false") boolean truncated,
            @Schema(description = "被截断省略的节点名称列表", example = "[]") List<String> truncatedNodeNames
    ) {}
}