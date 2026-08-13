package com.graphnexus.application.mastery.model;

/** 一次掌握度策略计算结果。 */
public record MasteryUpdateResult(
        Double oldWeight,
        double newWeight,
        double alpha,
        int sampleCount,
        double confidence
) {}
