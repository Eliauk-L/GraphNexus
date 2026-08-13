package com.graphnexus.application.mastery.model;

import java.time.LocalDateTime;

/** 面向 API 与学生画像 Tool 的知识点最新掌握度。 */
public record MasteryView(
        String knowledgePointId,
        String knowledgePointName,
        double weight,
        double confidence,
        int sampleCount,
        String lastExamNo,
        LocalDateTime lastUpdatedAt
) {}
