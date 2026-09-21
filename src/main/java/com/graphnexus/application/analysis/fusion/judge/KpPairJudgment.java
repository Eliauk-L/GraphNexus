package com.graphnexus.application.analysis.fusion.judge;

import java.util.Map;

/** Jev 对一个知识点候选对返回的可审计结构化判断。 */
public record KpPairJudgment(
        String provider,
        String model,
        String questionVersion,
        double linkScore,
        Map<Integer, Double> linkProbabilities,
        double confidence,
        double sameCoreConcept,
        double descriptionsConsistent,
        double neighborhoodConsistent,
        int inputTokens,
        int outputTokens,
        String stateHash,
        long durationMs) {

    public KpPairJudgment {
        linkProbabilities = Map.copyOf(linkProbabilities);
    }

    public JevLinkOutcome outcome() {
        return JevLinkOutcome.fromScore(linkScore);
    }
}
