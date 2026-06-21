package com.graphnexus.api.analysis.controller;

import com.graphnexus.api.analysis.dto.fusion.FusionExecuteVO;
import com.graphnexus.api.analysis.dto.fusion.FusionRollbackVO;
import com.graphnexus.api.analysis.dto.fusion.FusionStatusVO;
import com.graphnexus.application.analysis.fusion.model.FusionExecuteResult;
import com.graphnexus.application.analysis.fusion.model.FusionRollbackResult;
import com.graphnexus.application.analysis.fusion.model.FusionStatusResult;
import com.graphnexus.application.analysis.fusion.service.FusionService;
import com.graphnexus.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 宽图谱融合 REST API 控制器。
 *
 * @author Jay
 * @date 2026/06/15
 */
@RestController
@RequestMapping("/api/v1/analysis/fusion")
@RequiredArgsConstructor
@Tag(name = "宽图谱融合", description = "知识点（KP）融合合并与 MASTERS 掌握度聚合计算")
public class FusionController {

    private final FusionService fusionService;

    /**
     * 手动触发全量融合。
     */
    @Operation(summary = "手动全量融合", description = "对全图所有同名/相似的 KnowledgePoint 执行融合合并（FuzzyMatch，阈值 0.85），合并后重算所有受影响学生的 MASTERS 边权重（TimeDecay 时间衰减）。融合结果持久化到 fusion_log 表，支持回滚。融合期间拒绝并发请求")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "融合执行完成，返回融合日志 ID 及合并 KP 组数和 MASTERS 边数"),
            @ApiResponse(responseCode = "409", description = "A0017 融合操作正在进行中，拒绝并发"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @PostMapping("/execute")
    public ApiResult<FusionExecuteVO> execute() {
        FusionExecuteResult result = fusionService.fuseFull();
        return ApiResult.success(new FusionExecuteVO(
                result.fusionLogId(),
                result.mergedKpGroupCount(),
                result.mastersEdgeCount()
        ));
    }

    /**
     * 查询最近一次融合状态。
     */
    @Operation(summary = "查询融合状态", description = "返回最近一次融合操作的完整信息：触发方式（手动/自动增量）、状态、执行时间、合并 KP 组数、MASTERS 边数、融合明细 JSON（源KP→目标KP映射+边重定向）、MASTERS 变更快照 JSON（oldWeight→newWeight）")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "最近融合状态（如从未融合则 data=null）"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @GetMapping("/status")
    public ApiResult<FusionStatusVO> status() {
        FusionStatusResult result = fusionService.getStatus();
        if (result == null) {
            return ApiResult.success(null);
        }
        return ApiResult.success(new FusionStatusVO(
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
    @Operation(summary = "回滚融合", description = "按融合日志记录逆向恢复 Neo4j 图状态：恢复被合并的源 KP 节点、重定向被迁移的边、恢复到融合前的 MASTERS 权重。限制：① 仅可回滚最近一次融合；② 回滚后图状态有变更则无法再次回滚；③ 不支持跨多次融合的部分回滚")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "回滚完成，返回恢复的 KP 数和边数"),
            @ApiResponse(responseCode = "404", description = "A0016 融合日志不存在"),
            @ApiResponse(responseCode = "409", description = "A0018 图状态已变更，无法回滚"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @PostMapping("/rollback/{fusionLogId}")
    public ApiResult<FusionRollbackVO> rollback(
            @Parameter(description = "融合日志 ID", required = true, example = "1")
            @PathVariable Long fusionLogId) {
        FusionRollbackResult result = fusionService.rollback(fusionLogId);
        return ApiResult.success(new FusionRollbackVO(
                result.fusionLogId(),
                result.restoredKpCount(),
                result.restoredEdgeCount()
        ));
    }
}