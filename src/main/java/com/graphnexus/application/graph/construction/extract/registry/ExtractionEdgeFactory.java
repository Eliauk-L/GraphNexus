package com.graphnexus.application.graph.construction.extract.registry;

import com.graphnexus.infrastructure.neo4j.edge.GraphEdge;

/**
 * LLM 抽取关系类型 → 边对象的工厂（D3 / ADR-023）。
 *
 * <p>每个实现绑定一个 {@link EntityRelationType}，替代 ExtractionService.convertToDomain
 * 中的 switch 分支。新增关系类型 = 加枚举 + 注册本接口实现，不改 switch（AC-3）。</p>
 *
 * @author Jay
 * @date 2026/06/21
 */
public interface ExtractionEdgeFactory {

    /** 本工厂绑定的关系类型 */
    EntityRelationType relationType();

    /** 创建边对象 */
    GraphEdge create(String sourceNodeId, String targetNodeId, String description);
}
