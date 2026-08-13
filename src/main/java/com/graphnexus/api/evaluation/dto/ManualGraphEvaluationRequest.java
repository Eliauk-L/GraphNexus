package com.graphnexus.api.evaluation.dto;

import com.graphnexus.application.evaluation.graph.model.CandidateDependency;
import com.graphnexus.application.evaluation.graph.model.CandidateGraph;
import com.graphnexus.application.evaluation.graph.model.CandidateTopic;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Request used to verify the evaluation pipeline before LLM/NLP builders are connected. */
public record ManualGraphEvaluationRequest(
        @NotBlank String datasetVersion,
        @NotNull @Valid List<TopicRequest> topics,
        @NotNull @Valid List<DependencyRequest> dependencies
) {
    public CandidateGraph toCandidateGraph() {
        return new CandidateGraph(
                topics.stream().map(t -> new CandidateTopic(t.tempId(), t.name(), t.type(), t.domain(), t.description())).toList(),
                dependencies.stream().map(d -> new CandidateDependency(
                        d.prerequisiteTempId(), d.topicTempId(), d.strength())).toList());
    }

    public record TopicRequest(
            @NotBlank String tempId,
            @NotBlank String name,
            String type,
            String domain,
            String description
    ) {
    }

    public record DependencyRequest(
            @NotBlank String prerequisiteTempId,
            @NotBlank String topicTempId,
            String strength
    ) {
    }
}
