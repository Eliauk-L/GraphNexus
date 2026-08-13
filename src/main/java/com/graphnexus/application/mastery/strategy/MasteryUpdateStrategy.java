package com.graphnexus.application.mastery.strategy;

import com.graphnexus.application.mastery.model.ExamKnowledgeEvidence;
import com.graphnexus.application.mastery.model.MasteryState;
import com.graphnexus.application.mastery.model.MasteryUpdateResult;

public interface MasteryUpdateStrategy {
    MasteryUpdateResult update(MasteryState oldState, ExamKnowledgeEvidence evidence);

    String getName();
}
