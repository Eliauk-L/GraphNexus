package com.graphnexus.application.ops.snapshot.service;

/**
 * 统计快照采集服务接口。
 * 每日凌晨定时采集全量统计指标并持久化。
 *
 * @author Jay
 * @date 2026/06/23
 */
public interface SnapshotService {

    /**
     * 执行每日统计快照采集（由 @Scheduled 触发）。
     * 采集 usage + documents + graph 三层数据，
     * 各数据源独立 try-catch，单源失败不阻塞其他源（PARTIAL 模式）。
     */
    void takeDailySnapshot();
}