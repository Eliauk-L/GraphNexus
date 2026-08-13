package com.graphnexus.application.mastery.model;

/** 知识点当前掌握度状态；null weight 表示尚无历史状态。 */
public record MasteryState(Double weight, int sampleCount) {
    public static MasteryState empty() {
        return new MasteryState(null, 0);
    }
}
