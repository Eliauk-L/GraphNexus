package com.graphnexus.infrastructure.mysql.evaluation.repository;

import com.graphnexus.infrastructure.mysql.evaluation.entity.GraphEvaluationResultDO;
import com.graphnexus.infrastructure.mysql.evaluation.entity.GraphEvaluationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@EnabledIfSystemProperty(named = "evaluation.mysql.it", matches = "true")
class GraphEvaluationResultRepositoryTest {

    @Autowired private GraphEvaluationResultRepository repository;
    @Autowired private JdbcTemplate jdbcTemplate;
    private String groupId;

    @BeforeEach
    void prepareTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS graph_evaluation_result (
                  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, group_id VARCHAR(36) NOT NULL,
                  document_id BIGINT NOT NULL, dataset_version VARCHAR(64) NOT NULL,
                  dataset_hash VARCHAR(64) NOT NULL, text_hash VARCHAR(64) NOT NULL,
                  method VARCHAR(32) NOT NULL, repeat_index INT NOT NULL DEFAULT 1,
                  graph_id VARCHAR(64), status VARCHAR(20) NOT NULL, metrics_json MEDIUMTEXT,
                  duration_ms BIGINT, estimated_tokens BIGINT, error_message VARCHAR(512),
                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  INDEX idx_eval_group (group_id), INDEX idx_eval_document (document_id), INDEX idx_eval_status (status))
                """);
        groupId = UUID.randomUUID().toString();
    }

    @Test
    void persistsAndQueriesIndependentMethodRuns() {
        repository.save(run("LLM", 1));
        repository.save(run("NLP_NER_RE", 1));

        var runs = repository.findByGroupIdOrderByMethodAscRepeatIndexAsc(groupId);
        assertThat(runs).hasSize(2);
        assertThat(repository.findByStatus(GraphEvaluationStatus.PENDING)).extracting(GraphEvaluationResultDO::getGroupId)
                .contains(groupId);
        repository.deleteByGroupId(groupId);
        assertThat(repository.findByGroupIdOrderByMethodAscRepeatIndexAsc(groupId)).isEmpty();
    }

    private GraphEvaluationResultDO run(String method, int repeat) {
        return GraphEvaluationResultDO.builder().groupId(groupId).documentId(1L).datasetVersion("v1")
                .datasetHash("hash").textHash("text-hash").method(method).repeatIndex(repeat)
                .status(GraphEvaluationStatus.PENDING).build();
    }
}
