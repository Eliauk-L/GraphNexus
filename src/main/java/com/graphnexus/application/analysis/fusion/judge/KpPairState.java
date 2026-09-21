package com.graphnexus.application.analysis.fusion.judge;

/** 一个来源知识点与一个规范知识点候选组成的判断状态。 */
public record KpPairState(KnowledgePointDescriptor mention, KnowledgePointDescriptor candidate) {

    public KpPairState {
        if (mention == null || candidate == null) {
            throw new IllegalArgumentException("知识点来源与候选均不能为空");
        }
    }
}
