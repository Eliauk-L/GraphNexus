package com.graphnexus.application.analysis.fusion.judge;

/** `link_state` 三档 Score 的最近等级。 */
public enum JevLinkOutcome {
    DIFFERENT,
    REVIEW,
    SAME;

    public static JevLinkOutcome fromScore(double score) {
        if (score < 0 || score > 2) {
            throw new IllegalArgumentException("link_state score 必须位于 [0,2]");
        }
        int level = Math.min((int) Math.floor(score + 0.5), 2);
        return values()[level];
    }
}
