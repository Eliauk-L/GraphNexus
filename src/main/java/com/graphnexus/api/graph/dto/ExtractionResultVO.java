package com.graphnexus.api.graph.dto;

import com.graphnexus.application.graph.model.ExtractionResultBO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 抽取结果响应 VO。
 *
 * @author Jay
 * @date 2026/06/13
 */
@Data
@Schema(description = "知识图谱抽取结果视图")
public class ExtractionResultVO {

    @Schema(description = "文档 ID", example = "1")
    private Long documentId;

    @Schema(description = "抽取出的实体片段数量（DEFINITION/FORMULA/CONCEPT/EXAMPLE/SOLUTION）", example = "45")
    private int entityCount;

    @Schema(description = "抽取出的知识点数量", example = "12")
    private int knowledgePointCount;

    @Schema(description = "抽取出的知识分类数量", example = "3")
    private int categoryCount;

    @Schema(description = "生成的关系边总数（含 8 种边类型）", example = "68")
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