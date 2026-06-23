package com.graphnexus.api.config.controller;

import com.graphnexus.api.config.dto.UpdateConfigRequest;
import com.graphnexus.application.config.service.ConfigService;
import com.graphnexus.common.ApiResult;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 配置管理 API 控制器 — 仅 ADMIN 角色可访问。
 *
 * @author Jay
 * @date 2026/06/23
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ConfigController {

    private final ConfigService configService;

    /**
     * 查询所有配置列表（按 category + sortOrder 分组）。
     */
    @GetMapping
    public ApiResult<List<Map<String, Object>>> list() {
        return ApiResult.success(configService.listAll());
    }

    /**
     * 查询单个配置项详情。
     */
    @GetMapping("/{configKey}")
    public ApiResult<Map<String, Object>> get(@PathVariable String configKey) {
        Map<String, Object> config = configService.getAsMap(configKey);
        if (config == null) {
            throw new BusinessException(ErrorCode.A0001, "配置项不存在: " + configKey);
        }
        return ApiResult.success(config);
    }

    /**
     * 获取配置的生效内容 — 用于编辑器预填（TEXT 类型返回 classpath 原文）。
     */
    @GetMapping("/{configKey}/effective")
    public ApiResult<String> getEffectiveContent(@PathVariable String configKey) {
        String content = configService.getEffectiveContent(configKey);
        if (content == null) {
            throw new BusinessException(ErrorCode.A0001, "配置项不存在: " + configKey);
        }
        return ApiResult.success(content);
    }

    /**
     * 更新配置值（保存到 DB，不 Apply）。
     */
    @PutMapping("/{configKey}")
    public ApiResult<Map<String, Object>> update(@PathVariable String configKey,
                                             @Valid @RequestBody UpdateConfigRequest request) {
        Map<String, Object> updated = configService.updateAndGetMap(configKey, request.getConfigValue());
        return ApiResult.success(updated);
    }

    /**
     * 应用配置（从 DB 重载到运行中 Properties Bean）。
     */
    @PostMapping("/apply")
    public ApiResult<Map<String, Object>> apply() {
        Map<String, Object> result = configService.apply();
        return ApiResult.success(result);
    }
}