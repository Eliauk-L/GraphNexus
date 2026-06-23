package com.graphnexus.infrastructure.mysql.ops.repository;

import com.graphnexus.infrastructure.mysql.ops.entity.StatsSnapshotDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 统计快照 Repository。
 *
 * @author Jay
 * @date 2026/06/23
 */
@Repository
public interface StatsSnapshotRepository extends JpaRepository<StatsSnapshotDO, Long> {

    /**
     * 按快照日期精确查询。
     *
     * @param snapshotDate 快照日期
     * @return Optional 包裹的 StatsSnapshotDO
     */
    Optional<StatsSnapshotDO> findBySnapshotDate(LocalDate snapshotDate);

    /**
     * 按日期范围查询快照列表，按日期升序排列。
     *
     * @param start 开始日期（含）
     * @param end   结束日期（含）
     * @return 快照列表
     */
    List<StatsSnapshotDO> findBySnapshotDateBetweenOrderBySnapshotDateAsc(
            LocalDate start, LocalDate end);
}