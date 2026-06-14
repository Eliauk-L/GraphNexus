package com.graphnexus.application.graph.service;

import com.graphnexus.application.graph.model.ExtractionResultBO;
import com.graphnexus.application.graph.model.GraphSubgraphBO;

/**
 * 知识图谱服务接口。
 *
 * @author Jay
 * @date 2026/06/13
 */
public interface GraphService {

    /**
     * 触发文档知识图谱抽取。
     *
     * @param documentId MySQL document 表主键
     * @return 抽取结果摘要
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