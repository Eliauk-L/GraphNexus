package com.graphnexus.api.graph.controller;

import com.graphnexus.api.graph.dto.metrics.MetricResultVO;
import com.graphnexus.api.graph.dto.metrics.MetricsQueryRequest;
import com.graphnexus.application.graph.metrics.model.MetricResultBO;
import com.graphnexus.application.graph.metrics.service.MetricsService;
import com.graphnexus.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 图指标查询 API 控制器（L1）— PageRank 与度中心性端点。
 *
 * @author Jay
 * @date 2026/06/17
 */
@RestController
@RequestMapping("/api/v1/graph/metrics")
@RequiredArgsConstructor
@Tag(name = "图指标", description = "Neo4j GDS 图算法 — PageRank 节点重要性与度中心性查询")
@PreAuthorize("hasAnyRole('ADMIN','OPS_STAFF','OPS_MANAGER')")
public class MetricsController {

    private final MetricsService metricsService;

    /**
     * 查询 PageRank 值。
     */
    @Operation(summary = "查询 PageRank", description = "返回图中各节点的 PageRank 值（重要性排序）。指标计算以知识点或学生为中心：nodeTypes 必填且仅允许 KnowledgePoint/Student（Student 需同时含 KnowledgePoint）；边类型按中心自动收敛——含 KP 允许 PREREQUISITE_OF，含 Student 允许 MASTERS。GDS transient 命名图模式，计算后自动释放。结果 Caffeine 本地缓存（TTL 5 分钟），图谱变更后事件驱动自动清空缓存")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PageRank 结果列表"),
            @ApiResponse(responseCode = "400", description = "A0002 中心节点类型非法或边类型与中心不匹配"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常 / Neo4j GDS 计算失败")
    })
    @GetMapping("/pagerank")
    public ApiResult<List<MetricResultVO>> getPageRank(
            @Parameter(description = "中心节点类型列表（逗号分隔），仅允许 KnowledgePoint,Student。必填，Student 需同时含 KnowledgePoint", example = "KnowledgePoint", required = true)
            @RequestParam List<String> nodeTypes,
            @Parameter(description = "边类型列表（逗号分隔），与中心节点允许的边取交集。空 = 使用全部允许边", example = "PREREQUISITE_OF")
            @RequestParam(required = false) List<String> edgeTypes,
            @Parameter(description = "可选学科名称，传入后仅返回 BELONGS_TO_SUBJECT 指向该学科的节点指标", example = "数学")
            @RequestParam(required = false) String subject,
            @Parameter(description = "可选文档 ID（MySQL 主键），传入后仅返回该文档关联的 KP 指标（通过 EXTRACTS→ALIGNED_TO 路径）", example = "1")
            @RequestParam(required = false) String documentId) {

        MetricsQueryRequest request = new MetricsQueryRequest();
        request.setNodeTypes(nodeTypes);
        if (edgeTypes != null) request.setEdgeTypes(edgeTypes);

        List<MetricResultBO> results = metricsService.queryPageRank(
                request.nodeTypeSet(), request.edgeTypeSet(), subject, documentId);
        List<MetricResultVO> vos = results.stream()
                .map(MetricResultVO::from)
                .toList();
        return ApiResult.success(vos);
    }

    /**
     * 查询度中心性（含 inDegree + outDegree）。
     */
    @Operation(summary = "查询度中心性", description = "返回各节点的入度（inDegree）和出度（outDegree）值。指标计算以知识点或学生为中心：nodeTypes 必填且仅允许 KnowledgePoint/Student（Student 需同时含 KnowledgePoint）；边类型按中心自动收敛——含 KP 允许 PREREQUISITE_OF，含 Student 允许 MASTERS。缓存策略同 PageRank")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "度中心性结果列表"),
            @ApiResponse(responseCode = "400", description = "A0002 中心节点类型非法或边类型与中心不匹配"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常 / Neo4j GDS 计算失败")
    })
    @GetMapping("/degree")
    public ApiResult<List<MetricResultVO>> getDegree(
            @Parameter(description = "中心节点类型列表（逗号分隔），仅允许 KnowledgePoint,Student。必填，Student 需同时含 KnowledgePoint", example = "KnowledgePoint", required = true)
            @RequestParam List<String> nodeTypes,
            @Parameter(description = "边类型列表（逗号分隔），与中心节点允许的边取交集。空 = 使用全部允许边", example = "PREREQUISITE_OF")
            @RequestParam(required = false) List<String> edgeTypes,
            @Parameter(description = "可选学科名称，传入后仅返回 BELONGS_TO_SUBJECT 指向该学科的节点指标", example = "数学")
            @RequestParam(required = false) String subject,
            @Parameter(description = "可选文档 ID（MySQL 主键），传入后仅返回该文档关联的 KP 指标", example = "1")
            @RequestParam(required = false) String documentId) {

        MetricsQueryRequest request = new MetricsQueryRequest();
        request.setNodeTypes(nodeTypes);
        if (edgeTypes != null) request.setEdgeTypes(edgeTypes);

        List<MetricResultBO> results = metricsService.queryDegree(
                request.nodeTypeSet(), request.edgeTypeSet(), subject, documentId);
        List<MetricResultVO> vos = results.stream()
                .map(MetricResultVO::from)
                .toList();
        return ApiResult.success(vos);
    }
}