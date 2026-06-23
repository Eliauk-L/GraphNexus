package com.graphnexus.application.config.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.graphnexus.application.analysis.fusion.config.FusionProperties;
import com.graphnexus.application.analysis.fusion.config.FuzzyMatchProperties;
import com.graphnexus.application.analysis.fusion.config.TimeDecayProperties;
import com.graphnexus.application.config.service.ConfigService;
import com.graphnexus.application.config.validation.ConfigValidator;
import com.graphnexus.application.file.textbook.parser.pdf.mineru.config.MinerUProperties;
import com.graphnexus.application.query.chat.config.QueryProperties;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import com.graphnexus.infrastructure.llm.client.Langchain4jLlmGateway;
import com.graphnexus.infrastructure.mysql.config.entity.SystemConfigDO;
import com.graphnexus.infrastructure.mysql.config.repository.SystemConfigRepository;
import com.graphnexus.infrastructure.neo4j.gds.config.MetricsProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 配置管理业务实现 — CRUD + Caffeine 缓存 + Apply 热重载。
 *
 * <p>核心设计（见 ADR-041/042）：
 * <ul>
 *   <li>启动时 ConfigLoader 调 apply() 从 DB 加载到 Properties Bean</li>
 *   <li>运行时 PUT 仅写 DB + 缓存，不更新 Bean</li>
 *   <li>Apply 时全量重载 DB → setter 注入 Bean → 刷新缓存 → 按需重建 ChatModel</li>
 * </ul></p>
 *
 * @author Jay
 * @date 2026/06/23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigServiceImpl implements ConfigService {

    private final SystemConfigRepository systemConfigRepository;
    private final ConfigValidator configValidator;
    private final FusionProperties fusionProperties;
    private final FuzzyMatchProperties fuzzyMatchProperties;
    private final TimeDecayProperties timeDecayProperties;
    private final MetricsProperties metricsProperties;
    private final MinerUProperties minerUProperties;
    private final QueryProperties queryProperties;
    private final Langchain4jLlmGateway langchain4jLlmGateway;
    private final org.springframework.core.io.ResourceLoader resourceLoader;

    private static final String PROMPT_CLASSPATH_PREFIX = "classpath:/prompts/";

    /** 全量配置缓存 */
    private final Cache<String, SystemConfigDO> configCache = Caffeine.newBuilder()
            .initialCapacity(64)
            .build();

    /** 记录自上次 Apply 以来被修改过的 configKey（用于 applied 标记） */
    private final Set<String> modifiedSinceApply = ConcurrentHashMap.newKeySet();

    /** configKey → setter 映射表 */
    private Map<String, Consumer<String>> setterMap;

    @PostConstruct
    void initSetterMap() {
        setterMap = buildSetterMap();
    }

    @Override
    public List<Map<String, Object>> listAll() {
        List<SystemConfigDO> all = new ArrayList<>(configCache.asMap().values());
        all.sort(Comparator.comparingInt(SystemConfigDO::getSortOrder));

        List<Map<String, Object>> result = new ArrayList<>();
        for (SystemConfigDO config : all) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("configKey", config.getConfigKey());
            item.put("configName", config.getConfigName());
            item.put("configValue", config.getConfigValue());
            item.put("configType", config.getConfigType());
            item.put("category", config.getCategory());
            item.put("description", config.getDescription());
            item.put("defaultValue", config.getDefaultValue());
            item.put("required", config.getRequired());
            item.put("applied", !modifiedSinceApply.contains(config.getConfigKey()));
            item.put("isCustomized", config.getConfigValue() != null);
            result.add(item);
        }
        return result;
    }

    @Override
    public Map<String, Object> getAsMap(String configKey) {
        SystemConfigDO config = configCache.getIfPresent(configKey);
        if (config == null) return null;
        return toMap(config);
    }

    /**
     * 内部使用：获取 DO（供 loadFromDatabase 和 getPromptText 使用，不暴露给 L1）。
     */
    SystemConfigDO get(String configKey) {
        return configCache.getIfPresent(configKey);
    }

    @Override
    @Transactional
    public Map<String, Object> updateAndGetMap(String configKey, String configValue) {
        return toMap(doUpdate(configKey, configValue));
    }

    /**
     * 内部使用：更新配置并返回 DO。
     */
    SystemConfigDO update(String configKey, String configValue) {
        return doUpdate(configKey, configValue);
    }

    private SystemConfigDO doUpdate(String configKey, String configValue) {
        SystemConfigDO existing = configCache.getIfPresent(configKey);
        if (existing == null) {
            throw new BusinessException(ErrorCode.A0001,
                    "配置项不存在: " + configKey);
        }

        // 空值 = 恢复默认值（仅非必填项允许）
        boolean resetToDefault = (configValue == null || configValue.isBlank());
        if (resetToDefault && Boolean.TRUE.equals(existing.getRequired())) {
            throw new BusinessException(ErrorCode.A0032,
                    "配置项 [" + configKey + "] 为必填项，不能恢复默认值");
        }

        if (!resetToDefault) {
            configValidator.validate(configKey, existing.getConfigType(),
                    configValue, existing.getValidationRule());
        }

        // 持久化：恢复默认值时 config_value 设为 NULL
        existing.setConfigValue(resetToDefault ? null : configValue);
        systemConfigRepository.save(existing);

        // 更新缓存
        configCache.put(configKey, existing);

        // 标记未应用
        modifiedSinceApply.add(configKey);

        log.info("配置已更新（未Apply）: {} = {}", configKey, configValue);
        return existing;
    }

    @Override
    public Map<String, Object> apply() {
        List<SystemConfigDO> all = systemConfigRepository.findAll();
        int reloadedCount = 0;

        // 收集 LLM 模型参数变更
        String newModel = null;
        Double newTemperature = null;
        Integer newMaxTokens = null;
        boolean llmChanged = false;

        for (SystemConfigDO config : all) {
            if (config.getConfigValue() == null || config.getConfigValue().isBlank()) {
                continue;
            }

            Consumer<String> setter = setterMap.get(config.getConfigKey());
            if (setter != null) {
                try {
                    setter.accept(config.getConfigValue());
                    reloadedCount++;
                } catch (Exception e) {
                    log.error("Apply 配置失败: key={}, value={}, error={}",
                            config.getConfigKey(), config.getConfigValue(), e.getMessage());
                }
            }

            // 追踪 LLM 参数
            switch (config.getConfigKey()) {
                case "llm.base-url":
                case "llm.api-key":
                    llmChanged = true;
                    break;
                case "llm.model":
                    newModel = config.getConfigValue();
                    llmChanged = true;
                    break;
                case "llm.temperature":
                    try {
                        newTemperature = Double.parseDouble(config.getConfigValue());
                        llmChanged = true;
                    } catch (NumberFormatException ignored) {}
                    break;
                case "llm.max-tokens":
                    try {
                        newMaxTokens = Integer.parseInt(config.getConfigValue());
                        llmChanged = true;
                    } catch (NumberFormatException ignored) {}
                    break;
            }
        }

        // 刷新缓存
        configCache.invalidateAll();
        for (SystemConfigDO config : all) {
            configCache.put(config.getConfigKey(), config);
        }

        // 清除未应用标记
        modifiedSinceApply.clear();

        // 重建 LLM ChatModel
        if (llmChanged) {
            String currentModel = langchain4jLlmGateway.getCurrentModelName();
            Double currentTemp = langchain4jLlmGateway.getCurrentTemperature();
            Integer currentMaxTokens = langchain4jLlmGateway.getCurrentMaxTokens();

            String applyModel = newModel != null ? newModel : currentModel;
            Double applyTemp = newTemperature != null ? newTemperature : currentTemp;
            Integer applyMax = newMaxTokens != null ? newMaxTokens : currentMaxTokens;

            try {
                langchain4jLlmGateway.reinitialize(applyModel, applyTemp, applyMax);
                log.info("LLM ChatModel 已重建: model={}, temperature={}, maxTokens={}",
                        applyModel, applyTemp, applyMax);
            } catch (Exception e) {
                log.error("LLM ChatModel 重建失败，继续使用旧模型: {}", e.getMessage());
            }
        }

        log.info("Config applied: {} updated, {} total, reloadedAt={}",
                reloadedCount, all.size(), LocalDateTime.now());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reloadedCount", reloadedCount);
        result.put("reloadedAt", LocalDateTime.now().toString());
        return result;
    }

    @Override
    public String getPromptText(String configKey) {
        SystemConfigDO config = configCache.getIfPresent(configKey);
        if (config != null && config.getConfigValue() != null && !config.getConfigValue().isBlank()) {
            return config.getConfigValue();
        }
        return null;
    }

    @Override
    public String getEffectiveContent(String configKey) {
        SystemConfigDO config = configCache.getIfPresent(configKey);
        if (config == null) return null;

        // 已自定义 → 直接返回 DB 值
        if (config.getConfigValue() != null && !config.getConfigValue().isBlank()) {
            return config.getConfigValue();
        }

        // TEXT 类型（提示词）：从 classpath 加载原文作为编辑器预填内容
        if ("TEXT".equalsIgnoreCase(config.getConfigType()) && configKey.startsWith("prompt.")) {
            String templateName = configKey.substring("prompt.".length());
            String location = PROMPT_CLASSPATH_PREFIX + templateName + ".md";
            try {
                var resource = resourceLoader.getResource(location);
                if (resource.exists()) {
                    return resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                log.warn("无法从 classpath 加载提示词原文: {}", location, e);
            }
        }

        // 其他类型：返回 defaultValue
        return config.getDefaultValue();
    }

    /**
     * DO → Map 转换，供 L1 层使用（避免 Controller 直接依赖 DO）。
     */
    private Map<String, Object> toMap(SystemConfigDO config) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("configKey", config.getConfigKey());
        item.put("configName", config.getConfigName());
        item.put("configValue", config.getConfigValue());
        item.put("configType", config.getConfigType());
        item.put("category", config.getCategory());
        item.put("description", config.getDescription());
        item.put("defaultValue", config.getDefaultValue());
        item.put("required", config.getRequired());
        item.put("validationRule", config.getValidationRule());
        item.put("applied", !modifiedSinceApply.contains(config.getConfigKey()));
        item.put("isCustomized", config.getConfigValue() != null);
        return item;
    }

    /**
     * 仅用于 ConfigLoader 启动时加载：静默加载全部 DB 值到 Properties Bean。
     */
    public int loadFromDatabase() {
        List<SystemConfigDO> all = systemConfigRepository.findAll();
        int loaded = 0;

        // 预热缓存
        for (SystemConfigDO config : all) {
            configCache.put(config.getConfigKey(), config);
        }

        // 注入 Bean（仅 config_value 不为空的自定义值）
        for (SystemConfigDO config : all) {
            if (config.getConfigValue() == null || config.getConfigValue().isBlank()) {
                continue;
            }
            Consumer<String> setter = setterMap.get(config.getConfigKey());
            if (setter != null) {
                try {
                    setter.accept(config.getConfigValue());
                    loaded++;
                } catch (Exception e) {
                    log.warn("启动加载配置失败，使用 yml 默认值: key={}, error={}",
                            config.getConfigKey(), e.getMessage());
                }
            }
        }

        // 加载 LLM 模型参数
        loadLlmParams(all);

        log.info("Loaded {} configs from database (total {} keys)", loaded, all.size());
        return loaded;
    }

    private void loadLlmParams(List<SystemConfigDO> all) {
        String model = null;
        Double temperature = null;
        Integer maxTokens = null;
        boolean llmChanged = false;

        for (SystemConfigDO c : all) {
            if (c.getConfigValue() == null || c.getConfigValue().isBlank()) continue;
            switch (c.getConfigKey()) {
                case "llm.base-url":
                case "llm.api-key":
                    llmChanged = true;
                    break;
                case "llm.model": model = c.getConfigValue(); llmChanged = true; break;
                case "llm.temperature":
                    try { temperature = Double.parseDouble(c.getConfigValue()); llmChanged = true; } catch (NumberFormatException ignored) {}
                    break;
                case "llm.max-tokens":
                    try { maxTokens = Integer.parseInt(c.getConfigValue()); llmChanged = true; } catch (NumberFormatException ignored) {}
                    break;
            }
        }

        if (llmChanged) {
            try {
                String m = model != null ? model : langchain4jLlmGateway.getCurrentModelName();
                Double t = temperature != null ? temperature : langchain4jLlmGateway.getCurrentTemperature();
                Integer mt = maxTokens != null ? maxTokens : langchain4jLlmGateway.getCurrentMaxTokens();
                langchain4jLlmGateway.reinitialize(m, t, mt);
                log.info("LLM ChatModel 从 DB 加载: model={}, temperature={}, maxTokens={}", m, t, mt);
            } catch (Exception e) {
                log.warn("LLM ChatModel 从 DB 加载失败，使用 yml 默认值: {}", e.getMessage());
            }
        }
    }

    /**
     * 构建 configKey → setter 映射表。
     * 新增配置项时在此追加映射（v1 手动维护，v2 可注解自动化）。
     */
    private Map<String, Consumer<String>> buildSetterMap() {
        Map<String, Consumer<String>> map = new LinkedHashMap<>();

        // ── FusionProperties ──
        map.put("fusion.kp-matching.strategy",
                v -> fusionProperties.getMatching().setStrategy(v));
        map.put("fusion.kp-matching.threshold",
                v -> fusionProperties.getMatching().setThreshold(Double.parseDouble(v)));
        map.put("fusion.weight.strategy",
                v -> fusionProperties.getWeight().setStrategy(v));

        // ── FuzzyMatchProperties ──
        map.put("fusion.strategy.fuzzy.alpha",
                v -> fuzzyMatchProperties.setAlpha(Double.parseDouble(v)));
        map.put("fusion.strategy.fuzzy.beta",
                v -> fuzzyMatchProperties.setBeta(Double.parseDouble(v)));
        map.put("fusion.strategy.fuzzy.gamma",
                v -> fuzzyMatchProperties.setGamma(Double.parseDouble(v)));

        // ── TimeDecayProperties ──
        map.put("fusion.strategy.time-decay.factor",
                v -> timeDecayProperties.setFactor(Double.parseDouble(v)));

        // ── MetricsProperties（record 不可变，整体替换）──
        map.put("graph.metrics.cache.ttl-minutes", v -> {
            MetricsProperties.Cache old = metricsProperties.getCache();
            metricsProperties.setCache(new MetricsProperties.Cache(
                    v != null ? Integer.parseInt(v) : old.ttlMinutes(), old.maxSize()));
        });
        map.put("graph.metrics.cache.max-size", v -> {
            MetricsProperties.Cache old = metricsProperties.getCache();
            metricsProperties.setCache(new MetricsProperties.Cache(
                    old.ttlMinutes(), v != null ? Integer.parseInt(v) : old.maxSize()));
        });
        map.put("graph.metrics.page-rank.max-iterations", v -> {
            MetricsProperties.PageRank old = metricsProperties.getPageRank();
            metricsProperties.setPageRank(new MetricsProperties.PageRank(
                    v != null ? Integer.parseInt(v) : old.maxIterations(), old.dampingFactor()));
        });
        map.put("graph.metrics.page-rank.damping-factor", v -> {
            MetricsProperties.PageRank old = metricsProperties.getPageRank();
            metricsProperties.setPageRank(new MetricsProperties.PageRank(
                    old.maxIterations(), v != null ? Double.parseDouble(v) : old.dampingFactor()));
        });

        // ── MinerUProperties ──
        map.put("mineru.enabled",
                v -> minerUProperties.setEnabled(Boolean.parseBoolean(v)));
        map.put("mineru.api.poll-timeout", v -> {
            try {
                minerUProperties.getApi().setPollTimeout(
                        Duration.ofSeconds(Long.parseLong(v.replace("s", ""))));
            } catch (NumberFormatException e) {
                log.warn("无法解析 mineru.api.poll-timeout 值: {}", v);
            }
        });
        map.put("mineru.api.poll-interval", v -> {
            try {
                minerUProperties.getApi().setPollInterval(
                        Duration.ofSeconds(Long.parseLong(v.replace("s", ""))));
            } catch (NumberFormatException e) {
                log.warn("无法解析 mineru.api.poll-interval 值: {}", v);
            }
        });

        // ── QueryProperties ──
        map.put("query.token-budget.max-input-tokens",
                v -> queryProperties.getTokenBudget().setMaxInputTokens(Integer.parseInt(v)));
        map.put("query.token-budget.chars-per-token",
                v -> queryProperties.getTokenBudget().setCharsPerToken(Integer.parseInt(v)));
        map.put("query.pruning.weak-threshold",
                v -> queryProperties.getPruning().setWeakThreshold(Double.parseDouble(v)));
        map.put("query.pruning.max-prerequisite-hops",
                v -> queryProperties.getPruning().setMaxPrerequisiteHops(Integer.parseInt(v)));
        map.put("query.retry.max-retries",
                v -> queryProperties.getRetry().setMaxRetries(Integer.parseInt(v)));
        map.put("query.retry.retry-delay-ms",
                v -> queryProperties.getRetry().setRetryDelayMs(Long.parseLong(v)));
        map.put("query.output-format",
                v -> queryProperties.getOutput().setFormat(v));

        // ── LLM Gateway 参数（直接调 gateway setter）──
        map.put("llm.base-url", v -> langchain4jLlmGateway.setBaseUrl(v));
        map.put("llm.api-key", v -> langchain4jLlmGateway.setApiKey(v));
        map.put("llm.model", v -> langchain4jLlmGateway.setModel(v));
        map.put("llm.temperature", v -> langchain4jLlmGateway.setTemperature(Double.parseDouble(v)));
        map.put("llm.max-tokens", v -> langchain4jLlmGateway.setMaxTokens(Integer.parseInt(v)));

        return map;
    }
}