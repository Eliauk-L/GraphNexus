package com.graphnexus.application.graph.construction.extract.registry;

import com.graphnexus.infrastructure.neo4j.edge.GraphEdge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 关系边工厂注册表（D3 / ADR-023）。
 *
 * <p>构造期 Spring 注入所有 {@link ExtractionEdgeFactory} 实现按 {@link EntityRelationType} 索引；
 * {@link #create} 按类型路由到对应工厂，替代 convertToDomain 的 switch（AC-3）。</p>
 *
 * <p>沿用既有 {@code FileParserRegistry} 的注册表范式（构造期注入 List + 内部 Map 索引）。</p>
 *
 * @author Jay
 * @date 2026/06/21
 */
@Slf4j
@Component
public class ExtractionEdgeFactoryRegistry {

    private final Map<EntityRelationType, ExtractionEdgeFactory> factoryByType =
            new EnumMap<>(EntityRelationType.class);

    /**
     * Spring 自动注入所有 ExtractionEdgeFactory 实现并按 relationType 索引。
     */
    public ExtractionEdgeFactoryRegistry(List<ExtractionEdgeFactory> factories) {
        for (ExtractionEdgeFactory factory : factories) {
            ExtractionEdgeFactory prev = factoryByType.put(factory.relationType(), factory);
            if (prev != null) {
                throw new IllegalStateException(
                        "ExtractionEdgeFactory relationType 冲突: " + factory.relationType()
                                + " 已被 " + prev.getClass().getName() + " 注册");
            }
        }
        log.info("ExtractionEdgeFactoryRegistry 初始化完成，已注册 {} 个关系工厂: {}",
                factoryByType.size(), factoryByType.keySet());
    }

    /**
     * 按关系类型创建边。
     *
     * @throws IllegalStateException 该关系类型未注册工厂时
     */
    public GraphEdge create(EntityRelationType type, String sourceNodeId, String targetNodeId, String description) {
        ExtractionEdgeFactory factory = factoryByType.get(type);
        if (factory == null) {
            throw new IllegalStateException("未注册关系类型 " + type + " 的 ExtractionEdgeFactory");
        }
        return factory.create(sourceNodeId, targetNodeId, description);
    }
}
