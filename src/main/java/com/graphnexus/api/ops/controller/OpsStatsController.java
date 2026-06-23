package com.graphnexus.api.ops.controller;

import com.graphnexus.api.ops.dto.OpsTrendRequest;
import com.graphnexus.application.ops.stats.service.OpsStatsService;
import com.graphnexus.common.ApiResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 运营统计 API 控制器（L1）。
 * 仅 ADMIN 和 OPS_MANAGER 角色可访问。
 *
 * @author Jay
 * @date 2026/06/23
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ops/stats")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OPS_MANAGER')")
public class OpsStatsController {

    private final OpsStatsService opsStatsService;

    /** 运营仪表盘摘要数据（实时） */
    @GetMapping("/summary")
    public ApiResult<Object> getSummary() {
        return ApiResult.success(opsStatsService.getSummary());
    }

    /** 历史趋势时间序列（基于快照表） */
    @GetMapping("/trend")
    public ApiResult<Object> getTrend(@Valid OpsTrendRequest request) {
        return ApiResult.success(opsStatsService.getTrend(
                request.getMetric(), request.getGranularity(), request.getRange()));
    }

    /** Neo4j 中所有学科名称列表 */
    @GetMapping("/subjects")
    public ApiResult<List<String>> getSubjects() {
        return ApiResult.success(opsStatsService.getSubjects());
    }
}