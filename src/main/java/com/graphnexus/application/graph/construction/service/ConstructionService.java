package com.graphnexus.application.graph.construction.service;

import com.graphnexus.application.graph.construction.model.ExtractionResultBO;
import com.graphnexus.application.graph.construction.model.GraphSubgraphBO;

/**
 * 图谱构建服务接口 — 编排三阶段流水线（构建→实体对齐→图谱融合）。
 *
 * @author Jay
 * @date 2026/06/20
 */
public interface ConstructionService {

    /**
     * 触发文档知识图谱构建（三阶段流水线：构建→实体对齐→图谱融合）。
     *
     * @param documentId MySQL document 表主键
     * @return 构建结果摘要（含各类型节点和边数量，含 BELONGS_TO_SUBJECT 边）
     */
    ExtractionResultBO extract(Long documentId);

    /**
     * 查询文档的完整知识子图。
     *
     * @param documentId MySQL document 表主键
     * @return 子图（节点 + 边）
     */
    GraphSubgraphBO getSubgraph(Long documentId);
}