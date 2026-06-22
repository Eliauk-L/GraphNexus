package com.graphnexus.api.graph.controller;

import com.graphnexus.api.graph.dto.construction.ExtractionResultVO;
import com.graphnexus.api.graph.dto.construction.GraphSubgraphVO;
import com.graphnexus.application.graph.construction.model.ExtractionResultBO;
import com.graphnexus.application.graph.construction.model.GraphSubgraphBO;
import com.graphnexus.application.graph.construction.service.ConstructionService;
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
 * 图谱构建 REST API 控制器 — 两阶段流水线（构建→图谱融合）。
 *
 * @author Jay
 * @date 2026/06/20
 */
@RestController
@RequestMapping("/api/v1/graph/construction")
@RequiredArgsConstructor
@Tag(name = "图谱构建", description = "文档知识图谱抽取、文档子图查询 — 两阶段流水线（构建→图谱融合），跨文档实体对齐由融合隐式完成")
@PreAuthorize("hasRole('TEACHER')")
public class ConstructionController {

    private final ConstructionService constructionService;

    /**
     * 触发文档知识图谱构建（两阶段流水线：构建→图谱融合）。
     */
    @Operation(summary = "触发知识图谱构建", description = "对已解析完成的文档执行两阶段流水线：① 图谱构建（LLM 抽取 Entity/KP/Category + 关系边 + SubjectNode）→ ② 图谱融合（跨源 KP 合并 + MASTERS 重算，合并时自动重定向 ALIGNED_TO 边到规范 KP，隐式完成跨文档实体对齐）。重复抽取将全量覆盖旧子图数据")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "构建完成，返回各类型节点和边的数量（含 BELONGS_TO_SUBJECT 边）。若融合失败则附 fusionWarning"),
            @ApiResponse(responseCode = "400", description = "A0008 文档文本为空 / A0009 文档状态不允许抽取 / A0010 LLM 抽取结果校验失败"),
            @ApiResponse(responseCode = "404", description = "A0006 文档不存在或已删除"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常 / C0001 LLM API 调用失败")
    })
    @PostMapping("/extract/{documentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<ExtractionResultVO> extract(
            @Parameter(description = "文档 ID", required = true, example = "1")
            @PathVariable Long documentId) {
        ExtractionResultBO bo = constructionService.extract(documentId);
        return ApiResult.success(ExtractionResultVO.from(bo));
    }

    /**
     * 查询文档的知识子图（节点 + 边）。
     */
    @Operation(summary = "查询文档子图", description = "返回指定文档关联的所有 EntityNode、KnowledgePointNode、KnowledgeCategoryNode 及它们之间的关系边（ALIGNED_TO / BELONGS_TO / BELONGS_TO_SUBJECT / CHILD_OF / PREREQUISITE_OF / DERIVES / CONTAINS / REFERENCES / EXTRACTS）")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "文档子图（节点列表 + 边列表）"),
            @ApiResponse(responseCode = "404", description = "A0006 文档不存在或已删除"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @GetMapping("/document/{documentId}")
    public ApiResult<GraphSubgraphVO> getSubgraph(
            @Parameter(description = "文档 ID", required = true, example = "1")
            @PathVariable Long documentId) {
        GraphSubgraphBO bo = constructionService.getSubgraph(documentId);
        return ApiResult.success(GraphSubgraphVO.from(bo));
    }

    /**
     * 查询全量融合图谱（所有节点和边）。
     */
    @Operation(summary = "查询全量融合图谱", description = "返回 Neo4j 中所有节点和关系边，用于全量知识图谱可视化。适用于跨文档、跨学科的全景浏览")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "全量图谱（节点列表 + 边列表）"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @GetMapping("/full")
    public ApiResult<GraphSubgraphVO> getFullGraph() {
        GraphSubgraphBO bo = constructionService.getFullGraph();
        return ApiResult.success(GraphSubgraphVO.from(bo));
    }

    /**
     * 查询所有学科名称列表。
     */
    @Operation(summary = "查询学科列表", description = "返回 Neo4j 中所有 Subject 节点的名称，按名称排序。用于前端学科选择器下拉框数据源")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "学科名称列表（字符串数组）"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @GetMapping("/subjects")
    public ApiResult<List<String>> getSubjects() {
        List<String> subjects = constructionService.listSubjects();
        return ApiResult.success(subjects);
    }

    /**
     * 查询指定学科的知识全景图（跨文档 KP 聚合）。
     */
    @Operation(summary = "查询学科全景图", description = "返回指定学科下所有 KnowledgePoint 节点及其 PREREQUISITE_OF 依赖边、CHILD_OF 分类层级关系。聚合跨文档知识点，展示整个学科的知识结构骨架")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "学科全景图（节点列表 + 边列表）"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @GetMapping("/subject/{subjectName}")
    public ApiResult<GraphSubgraphVO> getSubjectGraph(
            @Parameter(description = "学科名称（如 数学、物理）", required = true, example = "数学")
            @PathVariable String subjectName) {
        GraphSubgraphBO bo = constructionService.getSubjectGraph(subjectName);
        return ApiResult.success(GraphSubgraphVO.from(bo));
    }
}