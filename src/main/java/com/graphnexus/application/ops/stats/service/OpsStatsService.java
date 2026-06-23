package com.graphnexus.application.ops.stats.service;

import com.graphnexus.application.ops.stats.model.OpsSummaryResponse;
import com.graphnexus.application.ops.stats.model.OpsTrendResponse;

import java.util.List;

/**
 * 运营统计聚合查询服务接口。
 *
 * @author Jay
 * @date 2026/06/23
 */
public interface OpsStatsService {

    /**
     * 获取运营仪表盘摘要数据（实时查询现网）。
     *
     * @return 三大维度聚合摘要
     */
    OpsSummaryResponse getSummary();

    /**
     * 获取指定指标的历史趋势数据（基于快照表）。
     *
     * @param metric      指标名（如 login_count / document_total / kp_count）
     * @param granularity 时间粒度（day / week / month）
     * @param range       时间范围（天数，如 30 表示最近 30 天）
     * @return 时间序列数据
     */
    OpsTrendResponse getTrend(String metric, String granularity, int range);

    /**
     * 获取 Neo4j 中所有学科名称列表。
     *
     * @return 学科名称数组
     */
    List<String> getSubjects();
}