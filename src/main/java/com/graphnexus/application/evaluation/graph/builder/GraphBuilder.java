package com.graphnexus.application.evaluation.graph.builder;

import com.graphnexus.application.evaluation.graph.model.GraphBuildContext;
import com.graphnexus.application.evaluation.graph.model.GraphBuildMethod;
import com.graphnexus.application.evaluation.graph.model.GraphBuildResult;

/** Extension point implemented independently by LLM and traditional NLP builders. */
public interface GraphBuilder {

    GraphBuildMethod method();

    GraphBuildResult build(GraphBuildContext context);
}
