package com.graphnexus.api.evaluation.dto;

import com.graphnexus.application.evaluation.graph.model.CandidateGraph;
import com.graphnexus.application.evaluation.graph.model.EvaluationMetrics;
import com.graphnexus.application.evaluation.graph.model.GraphEvaluationResult;

import java.time.LocalDateTime;
import java.util.List;

public record GraphEvaluationResponse(
        String graphId,
        String datasetVersion,
        String datasetHash,
        String method,
        String status,
        LocalDateTime createdAt,
        EvaluationMetrics metrics,
        CandidateGraph graph,
        List<String> warnings
) {
    public static GraphEvaluationResponse from(GraphEvaluationResult result) {
        return new GraphEvaluationResponse(result.graphId(), result.datasetVersion(), result.datasetHash(),
                result.method(), result.status(), result.createdAt(), result.metrics(), result.graph(), result.warnings());
    }
}
