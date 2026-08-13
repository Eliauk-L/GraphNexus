package com.graphnexus.application.mastery.strategy;

import com.graphnexus.application.mastery.config.MasteryProperties;
import com.graphnexus.application.mastery.model.ExamKnowledgeEvidence;
import com.graphnexus.application.mastery.model.MasteryState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmaMasteryUpdateStrategyTest {

    private MasteryProperties properties;
    private EmaMasteryUpdateStrategy strategy;

    @BeforeEach
    void setUp() {
        properties = new MasteryProperties();
        properties.getEma().setAlpha(0.3);
        strategy = new EmaMasteryUpdateStrategy(properties);
    }

    @Test
    void firstEvidenceInitializesFromScoreRate() {
        var result = strategy.update(MasteryState.empty(), evidence(0.8));
        assertEquals(0.8, result.newWeight(), 1e-8);
        assertEquals(1, result.sampleCount());
    }

    @Test
    void subsequentEvidenceUsesEma() {
        var result = strategy.update(new MasteryState(0.6, 2), evidence(0.9));
        assertEquals(0.69, result.newWeight(), 1e-8);
        assertEquals(3, result.sampleCount());
        assertEquals(1 - Math.exp(-1), result.confidence(), 1e-8);
    }

    @Test
    void rejectsInvalidAlpha() {
        properties.getEma().setAlpha(0);
        assertThrows(IllegalArgumentException.class,
                () -> strategy.update(MasteryState.empty(), evidence(0.5)));
    }

    private ExamKnowledgeEvidence evidence(double scoreRate) {
        return new ExamKnowledgeEvidence("S001", "E001", LocalDate.of(2026, 8, 1),
                "数学", "对称轴", scoreRate, List.of());
    }
}
