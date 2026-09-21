package com.graphnexus.application.analysis.fusion.judge;

/** 领域端口：对一个来源知识点与规范候选执行语义判断。 */
public interface KpPairJudge {

    KpPairJudgment judge(KpPairState state);
}
