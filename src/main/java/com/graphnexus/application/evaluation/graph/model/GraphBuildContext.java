package com.graphnexus.application.evaluation.graph.model;

/** Immutable input shared by all graph builders. */
public record GraphBuildContext(
        Long documentId,
        String documentName,
        String subject,
        String grade,
        String textSnapshot,
        String textHash,
        String chunkConfigVersion,
        String runId
) {
}
