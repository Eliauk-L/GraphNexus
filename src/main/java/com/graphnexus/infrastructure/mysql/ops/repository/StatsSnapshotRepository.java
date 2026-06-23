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

    Optional<StatsSnapshotDO> findBySnapshotDate(LocalDate snapshotDate);

    List<StatsSnapshotDO> findBySnapshotDateBetweenOrderBySnapshotDateAsc(
            LocalDate start, LocalDate end);
}