package com.graphnexus.application.analysis.model;

import java.util.Map;

/**
 * 图剪枝请求 BO — 包含意图标识、目标实体、学科及扩展参数。
 *
 * <p>由 {@code QueryService} 构建，传入
 * {@link com.graphnexus.application.analysis.strategy.SubgraphPruningStrategy#prune(PruningRequest)}。</p>
 *
 * <p>{@code intent} 使用 String 而非 query 模块的枚举类型，
 * 确保 analysis 模块不反向依赖 query 模块，维持单向依赖链。</p>
 *
 * @param intent   查询意图标识（如 "STUDENT_DIAGNOSIS"、"CLASS_WEAKNESS_OVERVIEW"），由调用方传入
 * @param entityId 目标实体标识（STUDENT_DIAGNOSIS→studentNo，CLASS_WEAKNESS_OVERVIEW→className）
 * @param subject  学科（如"数学"）
 * @param params   扩展参数（如 weakThreshold、maxHops）
 * @author Jay
 * @date 2026/06/17
 */
public record PruningRequest(
        String intent,
        String entityId,
        String subject,
        Map<String, Object> params
) {}