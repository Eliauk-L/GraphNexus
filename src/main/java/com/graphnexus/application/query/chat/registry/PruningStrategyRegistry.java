package com.graphnexus.application.query.chat.registry;

import com.graphnexus.application.analysis.strategy.SubgraphPruningStrategy;
import com.graphnexus.common.exception.BusinessException;
import com.graphnexus.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 剪枝策略注册表 — 按意图名称路由到对应的 {@link SubgraphPruningStrategy} 实现。
 *
 * <p>Spring 自动注入所有 {@link SubgraphPruningStrategy} bean，
 * key = bean name（与 {@code QueryIntent} 枚举名一致），value = 策略实例。
 * 新增意图只需实现接口 + {@code @Component("INTENT_NAME")} 注册，
 * 无需修改本类。</p>
 *
 * <p>Supersede ADR-010 的 switch-case 路由方式。</p>
 *
 * @author Jay
 * @date 2026/06/22
 * @see SubgraphPruningStrategy
 */
@Slf4j
@Component
public class PruningStrategyRegistry {

    private final Map<String, SubgraphPruningStrategy> strategyMap;

    /**
     * Spring 自动注入所有 {@link SubgraphPruningStrategy} bean。
     *
     * @param strategyMap bean name → 策略实例（Spring 自动装配）
     */
    public PruningStrategyRegistry(Map<String, SubgraphPruningStrategy> strategyMap) {
        this.strategyMap = Map.copyOf(strategyMap);
        log.info("剪枝策略注册表已初始化 ({} 个策略): {}",
                this.strategyMap.size(), this.strategyMap.keySet());
    }

    /**
     * 按意图名称获取剪枝策略。
     *
     * @param intentName 意图名（与 {@code QueryIntent} 枚举名一致，如 "STUDENT_DIAGNOSIS"）
     * @return 对应的剪枝策略实现
     * @throws BusinessException(A0019) 若意图无对应策略
     */
    public SubgraphPruningStrategy get(String intentName) {
        SubgraphPruningStrategy strategy = strategyMap.get(intentName);
        if (strategy == null) {
            throw new BusinessException(ErrorCode.A0019,
                    "不支持的查询意图: " + intentName);
        }
        return strategy;
    }
}