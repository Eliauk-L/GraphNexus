package com.graphnexus.application.mastery.model;

/** 单道题对知识点掌握度更新的可审计证据。 */
public record QuestionEvidence(
        String questionLabel,
        double rawScore,
        double maxScore
) {}
