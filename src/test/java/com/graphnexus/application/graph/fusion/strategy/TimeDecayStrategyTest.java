package com.graphnexus.application.graph.fusion.strategy;

import com.graphnexus.application.graph.fusion.config.TimeDecayProperties;
import com.graphnexus.application.graph.fusion.model.TestedRecord;
import com.graphnexus.application.graph.fusion.model.WeightResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TimeDecayStrategy 单元测试 — AC-4/5。
 *
 * @author Jay
 * @date 2026/06/15
 */
@DisplayName("TimeDecayStrategy 时间衰减加权")
class TimeDecayStrategyTest {

    private TimeDecayStrategy strategy;
    private LocalDate now;

    @BeforeEach
    void setUp() {
        TimeDecayProperties props = new TimeDecayProperties();
        props.setFactor(0.9);
        strategy = new TimeDecayStrategy(props);
        now = LocalDate.now();
    }

    @Test
    @DisplayName("AC-4: 两次考试时间衰减加权")
    void twoExamsTimeDecay() {
        LocalDate exam1 = now.minusMonths(3);  // 3 months ago
        LocalDate exam2 = now.minusMonths(1);  // 1 month ago

        List<TestedRecord> records = List.of(
                new TestedRecord(exam1, 3.0, 8.0, "对称轴"),   // 0.375
                new TestedRecord(exam2, 7.0, 10.0, "对称轴")   // 0.700
        );

        WeightResult result = strategy.calculate(records);
        // 期望 ≈ 0.554
        assertTrue(result.weight() > 0.50 && result.weight() < 0.60,
                "Expected weight ~0.554 but got " + result.weight());
        assertTrue(result.summaryJson().contains("\"examCount\": 2"));
    }

    @Test
    @DisplayName("单次考试时衰减抵消，weight = scoreRate")
    void singleExamNoDecayEffect() {
        LocalDate examDate = now.minusMonths(6);  // 半年前
        List<TestedRecord> records = List.of(
                new TestedRecord(examDate, 8.0, 10.0, "对称轴")  // 0.80
        );

        WeightResult result = strategy.calculate(records);
        // 单次考试：分子 = 0.80 × d, 分母 = d → d 抵消
        assertEquals(0.80, result.weight(), 0.01);
    }

    @Test
    @DisplayName("AC-5: 缺考不计入")
    void absentSkipped() {
        List<TestedRecord> records = List.of(
                new TestedRecord(now.minusMonths(1), null, 10.0, "判别式"), // 缺考
                new TestedRecord(now.minusMonths(1), 8.0, 10.0, "判别式")   // 0.80
        );

        WeightResult result = strategy.calculate(records);
        assertEquals(0.80, result.weight(), 0.01);
        assertTrue(result.summaryJson().contains("\"examCount\": 1"));
    }

    @Test
    @DisplayName("空记录返回 weight=0")
    void emptyRecordsReturnsZero() {
        WeightResult result = strategy.calculate(Collections.emptyList());
        assertEquals(0.0, result.weight(), 0.001);
        assertTrue(result.summaryJson().contains("\"examCount\": 0"));
    }

    @Test
    @DisplayName("全部缺考返回 weight=0")
    void allAbsentReturnsZero() {
        List<TestedRecord> records = List.of(
                new TestedRecord(now, null, 10.0, "测试"),
                new TestedRecord(now, null, 8.0, "测试")
        );
        WeightResult result = strategy.calculate(records);
        assertEquals(0.0, result.weight(), 0.001);
    }

    @Test
    @DisplayName("同考试多次考同一 KP 先取平均")
    void sameDayMultipleQuestionsAveraged() {
        List<TestedRecord> records = List.of(
                new TestedRecord(now.minusMonths(1), 6.0, 10.0, "对称轴"),  // 0.60
                new TestedRecord(now.minusMonths(1), 8.0, 10.0, "对称轴")   // 0.80
                // 同考试平均 = 0.70
        );
        WeightResult result = strategy.calculate(records);
        assertEquals(0.70, result.weight(), 0.01);
    }

    @Test
    @DisplayName("getName 返回 time-decay")
    void getNameReturnsTimeDecay() {
        assertEquals("time-decay", strategy.getName());
    }
}