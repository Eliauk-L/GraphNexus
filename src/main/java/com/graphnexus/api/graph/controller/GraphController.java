package com.graphnexus.api.graph.controller;

import com.graphnexus.api.graph.dto.ExtractionResultVO;
import com.graphnexus.api.graph.dto.GraphSubgraphVO;
import com.graphnexus.application.graph.model.ExtractionResultBO;
import com.graphnexus.application.graph.model.GraphSubgraphBO;
import com.graphnexus.application.graph.service.GraphService;
import com.graphnexus.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 知识图谱 REST API 控制器。
 *
 * <p>端点映射（见 DESIGN § D6）：</p>
 * <pre>
 *   POST /api/v1/graph/extract/{documentId}     触发抽取
 *   GET  /api/v1/graph/document/{documentId}    查询文档子图
 * </pre>
 *
 * @author Jay
 * @date 2026/06/13
 */
@RestController
@RequestMapping("/api/v1/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;

    /**
     * 触发文档知识图谱抽取。
     */
    @PostMapping("/extract/{documentId}")
    public ApiResponse<ExtractionResultVO> extract(@PathVariable Long documentId) {
        ExtractionResultBO bo = graphService.extract(documentId);
        return ApiResponse.success(ExtractionResultVO.from(bo));
    }

    /**
     * 查询文档的知识子图（节点 + 边）。
     */
    @GetMapping("/document/{documentId}")
    public ApiResponse<GraphSubgraphVO> getSubgraph(@PathVariable Long documentId) {
        GraphSubgraphBO bo = graphService.getSubgraph(documentId);
        return ApiResponse.success(GraphSubgraphVO.from(bo));
    }
}