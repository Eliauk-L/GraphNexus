package com.graphnexus.application.graph.metrics.service;

import com.graphnexus.application.graph.metrics.model.MetricResultBO;

import java.util.List;
import java.util.Set;

/**
 * 图指标计算服务接口 —— 指标查询 + 缓存管理。
 *
 * @author Jay
 * @date 2026/06/17
 */
public interface MetricsService {

    /**
     * 查询 PageRank 值。
     *
     * @param nodeTypes 节点类型标签集合（空 = 全类型）
     * @param edgeTypes 边类型集合（空 = 全类型）
     * @return 按值降序排列的 PageRank 结果
     */
    List<MetricResultBO> queryPageRank(Set<String> nodeTypes, Set<String> edgeTypes);

    /**
     * 查询度中心性（含 inDegree + outDegree）。
     *
     * @param nodeTypes 节点类型标签集合（空 = 全类型）
     * @param edgeTypes 边类型集合（空 = 全类型）
     * @return 每个节点两条记录：inDegree + outDegree
     */
    List<MetricResultBO> queryDegree(Set<String> nodeTypes, Set<String> edgeTypes);

    /**
     * 查询 PageRank 值（带学科过滤）。
     *
     * @param nodeTypes   节点类型标签集合（空 = 全类型）
     * @param edgeTypes   边类型集合（空 = 全类型）
     * @param subjectName 可选学科名称，非空时仅返回该学科节点的指标
     * @return 按值降序排列的 PageRank 结果
     */
    List<MetricResultBO> queryPageRank(Set<String> nodeTypes, Set<String> edgeTypes, String subjectName);

    /**
     * 查询度中心性（带学科过滤）。
     *
     * @param nodeTypes   节点类型标签集合（空 = 全类型）
     * @param edgeTypes   边类型集合（空 = 全类型）
     * @param subjectName 可选学科名称，非空时仅返回该学科节点的指标
     * @return 每个节点两条记录：inDegree + outDegree
     */
    List<MetricResultBO> queryDegree(Set<String> nodeTypes, Set<String> edgeTypes, String subjectName);

    /**
     * 查询 PageRank 值（按学科或文档范围过滤）。
     *
     * @param nodeTypes   节点类型标签集合
     * @param edgeTypes   边类型集合
     * @param subjectName 可选学科名称
     * @param documentId  可选文档 ID（MySQL 主键），用于按文档过滤 KP
     * @return 按值降序排列的 PageRank 结果
     */
    List<MetricResultBO> queryPageRank(Set<String> nodeTypes, Set<String> edgeTypes, String subjectName, String documentId);

    /**
     * 查询度中心性（按学科或文档范围过滤）。
     *
     * @param nodeTypes   节点类型标签集合
     * @param edgeTypes   边类型集合
     * @param subjectName 可选学科名称
     * @param documentId  可选文档 ID（MySQL 主键），用于按文档过滤 KP
     * @return 每个节点两条记录：inDegree + outDegree
     */
    List<MetricResultBO> queryDegree(Set<String> nodeTypes, Set<String> edgeTypes, String subjectName, String documentId);

    /**
     * 清空所有指标缓存（图谱变更时由事件监听器调用）。
     */
    void clearCache();
}