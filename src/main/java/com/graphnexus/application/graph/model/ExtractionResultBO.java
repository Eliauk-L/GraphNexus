package com.graphnexus.application.graph.model;

import lombok.Builder;
import lombok.Data;

/**
 * 抽取结果摘要 BO — 从 L2 Service 传到 L1 Controller。
 *
 * @author Jay
 * @date 2026/06/13
 */
@Data
@Builder
public class ExtractionResultBO {

    /** 关联的文档 ID（MySQL document 表主键） */
    private Long documentId;

    /** 抽取出的实体数量 */
    private int entityCount;

    /** 抽取出的知识点数量 */
    private int knowledgePointCount;

    /** 抽取出的知识分类数量 */
    private int categoryCount;

    /** 所有关系边数量 */
    private int edgeCount;
}