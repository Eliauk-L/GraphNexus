package com.graphnexus.api.system.controller;

import com.graphnexus.api.system.dto.SystemHealthVO;
import com.graphnexus.application.system.service.SystemHealthService;
import com.graphnexus.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统健康 REST API 控制器。
 *
 * @author Jay
 * @date 2026/06/23
 */
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
@Tag(name = "系统运维", description = "系统健康检测 + 日志查看")
@PreAuthorize("hasAnyRole('ADMIN','OPS_MANAGER','OPS_STAFF')")
public class SystemHealthController {

    private final SystemHealthService systemHealthService;

    @Operation(summary = "获取系统健康状态", description = "返回四组件健康状态（MySQL/Neo4j/Redis/MinIO）+ JVM 运行时指标")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "健康数据获取成功"),
            @ApiResponse(responseCode = "401", description = "A0025 未登录或 Token 过期"),
            @ApiResponse(responseCode = "403", description = "A0030 权限不足"),
            @ApiResponse(responseCode = "500", description = "B0001 系统内部异常")
    })
    @GetMapping("/health")
    public ApiResult<SystemHealthVO> getHealth() {
        return ApiResult.success(systemHealthService.getSystemHealth());
    }
}