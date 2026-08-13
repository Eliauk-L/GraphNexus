package com.graphnexus.infrastructure.mysql.agent.repository;

import com.graphnexus.infrastructure.mysql.agent.entity.AgentTaskDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentTaskRepository extends JpaRepository<AgentTaskDO, String> {
    List<AgentTaskDO> findTop50ByUserIdOrderByCreateTimeDesc(String userId);
    List<AgentTaskDO> findTop50ByOrderByCreateTimeDesc();
}
