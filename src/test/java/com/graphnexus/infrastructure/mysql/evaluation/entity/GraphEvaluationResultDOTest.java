package com.graphnexus.infrastructure.mysql.evaluation.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphEvaluationResultDOTest {

    @Test
    void supportsPendingRunningCompletedPromotedLifecycle() {
        GraphEvaluationResultDO result = GraphEvaluationResultDO.builder().build();
        result.transitionTo(GraphEvaluationStatus.RUNNING);
        result.complete("graph", "{}", 10, 20);
        result.transitionTo(GraphEvaluationStatus.PROMOTED);
        assertThat(result.getStatus()).isEqualTo(GraphEvaluationStatus.PROMOTED);
        assertThat(result.getGraphId()).isEqualTo("graph");
    }

    @Test
    void failureIsTerminalAndMessageIsBounded() {
        GraphEvaluationResultDO result = GraphEvaluationResultDO.builder().build();
        result.fail("x".repeat(600));
        assertThat(result.getStatus()).isEqualTo(GraphEvaluationStatus.FAILED);
        assertThat(result.getErrorMessage()).hasSize(512);
        assertThatThrownBy(() -> result.transitionTo(GraphEvaluationStatus.RUNNING))
                .hasMessageContaining("非法评测状态流转");
    }
}
