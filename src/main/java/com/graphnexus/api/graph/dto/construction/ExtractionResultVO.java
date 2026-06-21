package com.graphnexus.api.graph.dto.construction;

import com.graphnexus.application.graph.construction.model.ExtractionResultBO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 图谱构建结果响应 VO。
 *
 * @author Jay
 * @date 2026/06/13
 */
@Data
@Schema(description = "图谱构建结果视图")
public class ExtractionResultVO {

    @Schema(description = "文档 ID", example = "1")
    private Long documentId;

    @Schema(description = "抽取出的实体片段数量", example = "45")
    private int entityCount;

    @Schema(description = "抽取出的知识点数量", example = "12")
    private int knowledgePointCount;

    @Schema(description = "抽取出的知识分类数量", example = "3")
    private int categoryCount;

    @Schema(description = "生成的关系边总数（含 BELONGS_TO_SUBJECT 等）", example = "68")
    private int edgeCount;

    @Schema(description = "融合警告（融合失败时非空）", example = "增量融合失败：Neo4j 连接超时，可手动执行全量融合修复")
    private String fusionWarning;

    public static ExtractionResultVO from(ExtractionResultBO bo) {
        ExtractionResultVO vo = new ExtractionResultVO();
        vo.setDocumentId(bo.getDocumentId());
        vo.setEntityCount(bo.getEntityCount());
        vo.setKnowledgePointCount(bo.getKnowledgePointCount());
        vo.setCategoryCount(bo.getCategoryCount());
        vo.setEdgeCount(bo.getEdgeCount());
        vo.setFusionWarning(bo.getFusionWarning());
        return vo;
    }
}