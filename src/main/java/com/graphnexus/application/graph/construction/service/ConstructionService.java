package com.graphnexus.application.graph.construction.service;

import com.graphnexus.application.graph.construction.model.ExtractionResultBO;
import com.graphnexus.application.graph.construction.model.GraphSubgraphBO;

import java.util.List;

/**
 * 图谱构建服务接口 — 编排两阶段流水线（构建→图谱融合）。
 *
 * <p>跨文档实体对齐由融合隐式完成：融合合并重复 KP 时 redirectEdges 自动把
 * ALIGNED_TO 边重定向到规范 KP，无需独立对齐阶段。</p>
 *
 * @author Jay
 * @date 2026/06/20
 */
public interface ConstructionService {

    /**
     * 触发文档知识图谱构建（两阶段流水线：构建→图谱融合）。
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

    /**
     * 查询全量融合图谱（所有节点 + 所有边）。
     *
     * @return 全量图谱（节点 + 边）
     */
    GraphSubgraphBO getFullGraph();

    /**
     * 查询所有学科名称列表（从 Neo4j Subject 节点聚合）。
     *
     * @return 按名称排序的学科列表
     */
    List<String> listSubjects();

    /**
     * 查询指定学科的知识全景图（跨文档 KP 聚合视图）。
     *
     * @param subjectName 学科名称（如"数学"）
     * @return 子图（该学科所有 KP + PREREQUISITE_OF + CHILD_OF + KnowledgeCategory）
     */
    GraphSubgraphBO getSubjectGraph(String subjectName);
}