package com.graphnexus.infrastructure.mysql.query;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 问答任务 Repository — 日志类表，不设逻辑删除。
 *
 * <p>延用 Spring Data JPA 自动查询方法命名约定，无需显式 JPQL（无 Boolean/TINYINT 字段）。</p>
 *
 * @author Jay
 * @date 2026/06/17
 */
@Repository
public interface QueryTaskRepository extends JpaRepository<QueryTaskDO, Long> {

    /**
     * 按 taskId 精确查找（UUID，业务主键）。
     *
     * @param taskId 任务唯一标识
     * @return Optional 包裹的 QueryTaskDO
     */
    Optional<QueryTaskDO> findByTaskId(String taskId);
}