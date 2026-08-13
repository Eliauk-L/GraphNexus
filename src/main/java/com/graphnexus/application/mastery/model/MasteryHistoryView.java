package com.graphnexus.application.mastery.model;

import java.time.LocalDateTime;

/** 单知识点掌握度变化历史。 */
public record MasteryHistoryView(
        String eventId,
        String examNo,
        double scoreRate,
        Double oldWeight,
        double newWeight,
        double alpha,
        int sampleCount,
        double confidence,
        String evidenceJson,
        LocalDateTime occurredAt
) {}
