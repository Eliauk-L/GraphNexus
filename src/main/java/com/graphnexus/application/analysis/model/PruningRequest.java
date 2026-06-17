package com.graphnexus.application.analysis.model;

import com.graphnexus.application.query.model.QueryIntent;

import java.util.Map;

/**
 * 图剪枝请求 BO — 包含意图类型、目标实体、学科及扩展参数。
 *
 * <p>由 {@code QueryService} 构建，传入
 * {@link com.graphnexus.application.analysis.strategy.SubgraphPruningStrategy#prune(PruningRequest)}。</p>
 *
 * @param intent   查询意图类型
 * @param entityId 目标实体标识（如 studentNo）
 * @param subject  学科（如"数学"）
 * @param params   扩展参数（如 weakThreshold、maxHops）
 * @author Jay
 * @date 2026/06/17
 */
public record PruningRequest(
        QueryIntent intent,
        String entityId,
        String subject,
        Map<String, Object> params
) {}