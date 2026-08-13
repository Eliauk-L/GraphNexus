package com.graphnexus.api.evaluation.controller;

import com.graphnexus.api.evaluation.dto.GraphEvaluationResponse;
import com.graphnexus.api.evaluation.dto.ManualGraphEvaluationRequest;
import com.graphnexus.application.evaluation.graph.service.GraphEvaluationService;
import com.graphnexus.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/evaluations/graph")
@RequiredArgsConstructor
@Tag(name = "图谱质量评测", description = "黄金集驱动的知识图谱质量评测")
public class GraphEvaluationController {

    private final GraphEvaluationService graphEvaluationService;

    @PostMapping("/manual-runs")
    @Operation(summary = "提交手工候选图并同步完成严格评测")
    public ApiResult<GraphEvaluationResponse> evaluateManual(@Valid @RequestBody ManualGraphEvaluationRequest request) {
        return ApiResult.success(GraphEvaluationResponse.from(
                graphEvaluationService.evaluateManual(request.datasetVersion(), request.toCandidateGraph())));
    }

    @GetMapping("/graphs/{graphId}")
    @Operation(summary = "从 Neo4j 读取一份隔离的评测图及指标")
    public ApiResult<GraphEvaluationResponse> getGraph(@PathVariable String graphId) {
        return ApiResult.success(GraphEvaluationResponse.from(graphEvaluationService.getGraph(graphId)));
    }
}
