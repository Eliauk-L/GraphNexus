package com.graphnexus.api.graph.dto;

import com.graphnexus.application.graph.model.ExtractionResultBO;
import lombok.Data;

/**
 * 抽取结果响应 VO。
 *
 * @author Jay
 * @date 2026/06/13
 */
@Data
public class ExtractionResultVO {

    private Long documentId;
    private int entityCount;
    private int knowledgePointCount;
    private int categoryCount;
    private int edgeCount;

    public static ExtractionResultVO from(ExtractionResultBO bo) {
        ExtractionResultVO vo = new ExtractionResultVO();
        vo.setDocumentId(bo.getDocumentId());
        vo.setEntityCount(bo.getEntityCount());
        vo.setKnowledgePointCount(bo.getKnowledgePointCount());
        vo.setCategoryCount(bo.getCategoryCount());
        vo.setEdgeCount(bo.getEdgeCount());
        return vo;
    }
}