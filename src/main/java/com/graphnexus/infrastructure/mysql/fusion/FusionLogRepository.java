package com.graphnexus.infrastructure.mysql.fusion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 融合日志 Repository。
 *
 * <p>延用显式 JPQL 模式（参考 {@code ExamRecordRepository}），
 * 规避 Hibernate 6.5 Boolean/TINYINT 谓词 bug。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
@Repository
public interface FusionLogRepository extends JpaRepository<FusionLogDO, Long> {

    /**
     * 查询最近一条指定状态的融合日志（并发控制用）。
     */
    Optional<FusionLogDO> findTopByStatusOrderByExecutedAtDesc(String status);

    /**
     * 查询最近一条融合日志（状态查询用）。
     */
    Optional<FusionLogDO> findTopByOrderByExecutedAtDesc();
}