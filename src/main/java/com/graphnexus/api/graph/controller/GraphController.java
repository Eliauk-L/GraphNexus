package com.graphnexus.api.graph.controller;

import com.graphnexus.api.graph.dto.ExtractionResultVO;
import com.graphnexus.api.graph.dto.GraphSubgraphVO;
import com.graphnexus.application.graph.model.ExtractionResultBO;
import com.graphnexus.application.graph.model.GraphSubgraphBO;
import com.graphnexus.application.graph.service.GraphService;
import com.graphnexus.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 知识图谱 REST API 控制器。
 *
 * @author Jay
 * @date 2026/06/13
 */
@RestController
@RequestMapping("/api/v1/graph")
@RequiredArgsConstructor
@Tag(name = "知识图谱", description = "文档知识图谱抽取与文档子图查询")
public class GraphController {

    private final GraphService graphService;

    /**
     * 触发文档知识图谱抽取。
     */
    @Operation(summary = "触发知识图谱抽取", description = "对已解析完成的文档执行 LLM 抽取，从文档文本中抽取出 Entity/KP/Category 节点及关系边（含 PREREQUISITE_OF 前置依赖）。重复抽取将全量覆盖旧子图数据")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "抽取完成，返回各类型节点和边的数量"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "A0008 文档文本为空 / A0009 文档状态不允许抽取 / A0010 LLM 抽取结果校验失败"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "A0006 文档不存在或已删除"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "B0001 系统内部异常 / C0001 LLM API 调用失败")
    })
    @PostMapping("/extract/{documentId}")
    public ApiResponse<ExtractionResultVO> extract(
            @Parameter(description = "文档 ID", required = true, example = "1")
            @PathVariable Long documentId) {
        ExtractionResultBO bo = graphService.extract(documentId);
        return ApiResponse.success(ExtractionResultVO.from(bo));
    }

    /**
     * 查询文档的知识子图（节点 + 边）。
     */
    @Operation(summary = "查询文档子图", description = "返回指定文档关联的所有 EntityNode、KnowledgePointNode、KnowledgeCategoryNode 及它们之间的关系边（ALIGNED_TO / BELONGS_TO / CHILD_OF / PREREQUISITE_OF / DERIVES / CONTAINS / REFERENCES / EXTRACTS）")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "文档子图（节点列表 + 边列表）"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "A0006 文档不存在或已删除"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @GetMapping("/document/{documentId}")
    public ApiResponse<GraphSubgraphVO> getSubgraph(
            @Parameter(description = "文档 ID", required = true, example = "1")
            @PathVariable Long documentId) {
        GraphSubgraphBO bo = graphService.getSubgraph(documentId);
        return ApiResponse.success(GraphSubgraphVO.from(bo));
    }
}