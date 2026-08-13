package com.graphnexus.infrastructure.mysql.agent.repository;

import com.graphnexus.infrastructure.mysql.agent.entity.AgentToolCallDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentToolCallRepository extends JpaRepository<AgentToolCallDO, Long> {
    List<AgentToolCallDO> findByTaskIdOrderByRoundNoAsc(String taskId);
}
