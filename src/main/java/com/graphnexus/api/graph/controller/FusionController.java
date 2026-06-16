package com.graphnexus.api.graph.controller;

import com.graphnexus.api.graph.dto.FusionExecuteVO;
import com.graphnexus.api.graph.dto.FusionRollbackVO;
import com.graphnexus.api.graph.dto.FusionStatusVO;
import com.graphnexus.application.graph.fusion.model.FusionExecuteResult;
import com.graphnexus.application.graph.fusion.model.FusionRollbackResult;
import com.graphnexus.application.graph.fusion.model.FusionStatusResult;
import com.graphnexus.application.graph.fusion.service.FusionService;
import com.graphnexus.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 宽图谱融合 REST API 控制器。
 *
 * <p>端点映射（见 DESIGN §2.1-2.3）：</p>
 * <pre>
 *   POST /api/v1/graph/fusion/execute             手动全量融合
 *   GET  /api/v1/graph/fusion/status              查询融合状态
 *   POST /api/v1/graph/fusion/rollback/{logId}    回滚指定融合
 * </pre>
 *
 * @author Jay
 * @date 2026/06/15
 */
@RestController
@RequestMapping("/api/v1/graph/fusion")
@RequiredArgsConstructor
public class FusionController {

    private final FusionService fusionService;

    /**
     * 手动触发全量融合。
     */
    @PostMapping("/execute")
    public ApiResponse<FusionExecuteVO> execute() {
        FusionExecuteResult result = fusionService.fuseFull();
        return ApiResponse.success(new FusionExecuteVO(
                result.fusionLogId(),
                result.mergedKpGroupCount(),
                result.mastersEdgeCount()
        ));
    }

    /**
     * 查询最近一次融合状态。
     */
    @GetMapping("/status")
    public ApiResponse<FusionStatusVO> status() {
        FusionStatusResult result = fusionService.getStatus();
        if (result == null) {
            return ApiResponse.success(null);
        }
        return ApiResponse.success(new FusionStatusVO(
                result.fusionLogId(),
                result.triggerType(),
                result.status(),
                result.executedAt(),
                result.mergedKpGroupCount(),
                result.mastersEdgeCount(),
                result.rolledBack(),
                result.fusionDetailJson(),
                result.mastersSnapshotJson()
        ));
    }

    /**
     * 基于日志回滚指定融合操作。
     */
    @PostMapping("/rollback/{fusionLogId}")
    public ApiResponse<FusionRollbackVO> rollback(@PathVariable Long fusionLogId) {
        FusionRollbackResult result = fusionService.rollback(fusionLogId);
        return ApiResponse.success(new FusionRollbackVO(
                result.fusionLogId(),
                result.restoredKpCount(),
                result.restoredEdgeCount()
        ));
    }
}