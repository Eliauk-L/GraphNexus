CREATE TABLE IF NOT EXISTS agent_task (
    task_id          VARCHAR(36) NOT NULL,
    user_id          VARCHAR(128) NOT NULL,
    status           VARCHAR(20) NOT NULL,
    answer_text      MEDIUMTEXT,
    fallback_reason  VARCHAR(64),
    create_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (task_id),
    INDEX idx_agent_task_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 任务执行摘要';

CREATE TABLE IF NOT EXISTS agent_tool_call (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    task_id          VARCHAR(36) NOT NULL,
    round_no         INT NOT NULL,
    tool_name        VARCHAR(64) NOT NULL,
    arguments_json   JSON NOT NULL,
    observation_json MEDIUMTEXT,
    decision_summary VARCHAR(512),
    status           VARCHAR(20) NOT NULL,
    elapsed_ms       BIGINT,
    error_message    VARCHAR(1000),
    create_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_task_round (task_id, round_no),
    INDEX idx_agent_tool_task (task_id, round_no),
    CONSTRAINT fk_agent_tool_task FOREIGN KEY (task_id) REFERENCES agent_task(task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 可审计工具调用轨迹';
