package com.graphnexus.application.config.service;

import java.util.List;
import java.util.Map;

/**
 * 配置管理业务接口 — CRUD + 缓存 + Apply 热重载。
 *
 * @author Jay
 * @date 2026/06/23
 */
public interface ConfigService {

    /**
     * 查询所有配置项，按 category + sortOrder 分组排序。
     */
    List<Map<String, Object>> listAll();

    /**
     * 按 configKey 查询单个配置（返回 Map，避免 L1 直接依赖 L3 实体）。
     */
    Map<String, Object> getAsMap(String configKey);

    /**
     * 更新配置值（校验 → 持久化 DB → 更新缓存），返回 Map。
     */
    Map<String, Object> updateAndGetMap(String configKey, String configValue);

    /**
     * 应用配置：从 DB 全量重载 → 更新 @ConfigurationProperties Bean → 刷新缓存 → 按需重建 ChatModel。
     *
     * @return {"reloadedCount": N, "reloadedAt": ISO timestamp}
     */
    Map<String, Object> apply();

    /**
     * 获取提示词文本 — 用于双源加载（DB优先 → classpath fallback）。
     *
     * @param configKey 如 "prompt.student-diagnosis-system"
     * @return 自定义提示词文本，未自定义时返回 null（调用方自行 fallback classpath）
     */
    String getPromptText(String configKey);

    /**
     * 获取配置的生效内容 — 用于编辑器预填。
     * TEXT 类型：DB 自定义值 → classpath 文件原文（fallback）
     * 其他类型：configValue ?? defaultValue
     *
     * @param configKey 配置键
     * @return 生效的文本内容
     */
    String getEffectiveContent(String configKey);
}