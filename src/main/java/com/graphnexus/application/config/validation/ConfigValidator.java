package com.graphnexus.application.config.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 配置值校验器 — 按 configType 执行类型和范围校验。
 *
 * <p>校验规则：NUMBER→parseDouble+min/max范围、BOOLEAN→"true"/"false"、
 * STRING→非空检查（required=true时）、TEXT→非空检查（required=true时）。
 * validationRule 为 JSON 格式（如 {"min":0,"max":1}），解析失败时 WARN 跳过范围校验。</p>
 *
 * @author Jay
 * @date 2026/06/23
 */
@Slf4j
@Component
public class ConfigValidator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 校验配置值。
     *
     * <p>值为空（null 或 blank）时跳过类型校验，表示恢复默认值。</p>
     *
     * @param configKey          配置键（仅用于日志和异常消息）
     * @param configType         配置类型：NUMBER|STRING|BOOLEAN|TEXT
     * @param value              待校验的值（null/blank = 恢复默认值）
     * @param validationRuleJson 校验规则 JSON（可为 null）
     * @throws BusinessException 校验失败时抛出对应 ErrorCode
     */
    public void validate(String configKey, String configType, String value, String validationRuleJson) {
        // 空值 = 恢复默认值，跳过类型校验
        if (value == null || value.isBlank()) {
            return;
        }
        switch (configType.toUpperCase()) {
            case "NUMBER":
                validateNumber(configKey, value, validationRuleJson);
                break;
            case "BOOLEAN":
                validateBoolean(configKey, value);
                break;
            case "STRING":
                // STRING 类型无特殊类型校验，仅非空由 required 控制（在调用方处理）
                break;
            case "TEXT":
                // TEXT 类型无特殊类型校验，仅非空由 required 控制（在调用方处理）
                break;
            default:
                log.warn("未知的 configType: {}，跳过类型校验", configType);
        }
    }

    /**
     * 校验非空 — 用于 required=true 的 STRING/TEXT/任意类型。
     */
    public void validateRequired(String configKey, String configType, String value, boolean required) {
        if (required && (value == null || value.isBlank())) {
            throw new BusinessException(ErrorCode.A0032,
                    "配置项 [" + configKey + "] 为必填项，值不能为空");
        }
    }

    private void validateNumber(String configKey, String value, String validationRuleJson) {
        double numericValue;
        try {
            numericValue = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.A0031,
                    "配置项 [" + configKey + "] 期望 NUMBER 类型，实际值: " + value);
        }

        Double min = null;
        Double max = null;
        if (validationRuleJson != null && !validationRuleJson.isBlank()) {
            try {
                var node = OBJECT_MAPPER.readTree(validationRuleJson);
                if (node.has("min")) {
                    min = node.get("min").asDouble();
                }
                if (node.has("max")) {
                    max = node.get("max").asDouble();
                }
            } catch (JsonProcessingException e) {
                log.warn("validationRule JSON 解析失败，跳过范围校验: configKey={}, rule={}",
                        configKey, validationRuleJson, e);
            }
        }

        if (min != null && numericValue < min) {
            throw new BusinessException(ErrorCode.A0033,
                    "配置项 [" + configKey + "] 值 " + numericValue + " 小于最小值 " + min);
        }
        if (max != null && numericValue > max) {
            throw new BusinessException(ErrorCode.A0033,
                    "配置项 [" + configKey + "] 值 " + numericValue + " 大于最大值 " + max);
        }
    }

    private void validateBoolean(String configKey, String value) {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new BusinessException(ErrorCode.A0031,
                    "配置项 [" + configKey + "] 期望 BOOLEAN 类型（true/false），实际值: " + value);
        }
    }
}