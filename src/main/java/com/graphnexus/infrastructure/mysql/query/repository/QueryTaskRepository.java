package com.graphnexus.infrastructure.mysql.query.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import com.graphnexus.infrastructure.mysql.query.entity.QueryTaskDO;
import com.graphnexus.infrastructure.mysql.query.entity.QueryTaskStatus;

import java.util.Optional;

/**
 * 问答任务 Repository — 日志类表，不设逻辑删除。
 *
 * <p>延用 Spring Data JPA 自动查询方法命名约定，无需显式 JPQL（无 Boolean/TINYINT 字段）。
 * 扩展 {@link JpaSpecificationExecutor} 支持动态筛选查询（见 ADR-030）。</p>
 *
 * @author Jay
 * @date 2026/06/17
 */
@Repository
public interface QueryTaskRepository extends JpaRepository<QueryTaskDO, Long>,
        JpaSpecificationExecutor<QueryTaskDO> {

    /**
     * 按 taskId 精确查找（UUID，业务主键）。
     *
     * @param taskId 任务唯一标识
     * @return Optional 包裹的 QueryTaskDO
     */
    Optional<QueryTaskDO> findByTaskId(String taskId);
}