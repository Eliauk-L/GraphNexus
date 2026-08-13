package com.graphnexus.application.evaluation.graph.service;

import com.graphnexus.application.evaluation.graph.model.CandidateGraph;
import com.graphnexus.application.evaluation.graph.model.GraphEvaluationResult;

/** Application boundary for the manual evaluation vertical slice. */
public interface GraphEvaluationService {

    GraphEvaluationResult evaluateManual(String datasetVersion, CandidateGraph candidateGraph);

    GraphEvaluationResult getGraph(String graphId);
}
