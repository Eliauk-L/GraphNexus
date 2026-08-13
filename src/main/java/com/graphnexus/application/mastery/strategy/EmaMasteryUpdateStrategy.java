package com.graphnexus.application.mastery.strategy;

import com.graphnexus.application.mastery.config.MasteryProperties;
import com.graphnexus.application.mastery.model.ExamKnowledgeEvidence;
import com.graphnexus.application.mastery.model.MasteryState;
import com.graphnexus.application.mastery.model.MasteryUpdateResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 使用指数移动平均平滑单次考试波动。 */
@Component("ema")
@RequiredArgsConstructor
public class EmaMasteryUpdateStrategy implements MasteryUpdateStrategy {

    private final MasteryProperties properties;

    @Override
    public MasteryUpdateResult update(MasteryState oldState, ExamKnowledgeEvidence evidence) {
        if (oldState == null) oldState = MasteryState.empty();
        double alpha = properties.getEma().getAlpha();
        if (alpha <= 0 || alpha > 1) {
            throw new IllegalArgumentException("mastery.ema.alpha 必须处于 (0,1]");
        }
        double scoreRate = clamp(evidence.scoreRate());
        double newWeight = oldState.weight() == null
                ? scoreRate
                : alpha * scoreRate + (1 - alpha) * oldState.weight();
        int sampleCount = oldState.sampleCount() + 1;
        double confidence = 1 - Math.exp(-sampleCount / 3.0);
        return new MasteryUpdateResult(oldState.weight(), newWeight, alpha,
                sampleCount, confidence);
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(value, 1));
    }

    @Override
    public String getName() {
        return "ema";
    }
}
