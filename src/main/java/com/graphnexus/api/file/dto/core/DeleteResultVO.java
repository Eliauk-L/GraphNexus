package com.graphnexus.api.file.dto.core;

import com.graphnexus.application.file.core.model.DeleteResultBO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 级联删除结果 VO（L1 返回前端，对应 AC-7 响应体）。
 *
 * @author Jay
 * @date 2026/06/15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "成绩级联删除结果视图")
public class DeleteResultVO {

    @Schema(description = "被删除的考试编号", example = "E20200041")
    private String examNo;

    @Schema(description = "删除的 MySQL 记录数", example = "45")
    private int deletedRecordCount;

    @Schema(description = "被删除的 MinIO CSV 文件路径", example = "grades/2026/06/E20200041_scores.csv")
    private String filePath;

    @Schema(description = "删除的 Neo4j 边数（ATTENDED + TESTED）", example = "90")
    private int deletedEdgeCount;

    public static DeleteResultVO from(DeleteResultBO bo) {
        return DeleteResultVO.builder()
                .examNo(bo.getExamNo())
                .deletedRecordCount(bo.getDeletedRecordCount())
                .filePath(bo.getFilePath())
                .deletedEdgeCount(bo.getDeletedEdgeCount())
                .build();
    }
}