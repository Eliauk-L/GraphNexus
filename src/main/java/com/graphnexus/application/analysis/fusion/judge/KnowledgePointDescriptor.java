package com.graphnexus.application.analysis.fusion.judge;

import java.util.List;

/** 供知识点候选对语义判断使用的最小、无个人信息状态。 */
public record KnowledgePointDescriptor(
        String id,
        String name,
        String description,
        String subject,
        String stage,
        String grade,
        List<String> chapterPath,
        List<String> prerequisites,
        List<String> aliases) {

    public KnowledgePointDescriptor {
        chapterPath = chapterPath == null ? List.of() : List.copyOf(chapterPath);
        prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }
}
