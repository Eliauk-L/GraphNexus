package com.graphnexus.api.document.dto;

import com.graphnexus.application.document.model.DeleteResultBO;
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
public class DeleteResultVO {

    private String examNo;
    private int deletedRecordCount;
    private String minioPath;
    private int deletedEdgeCount;

    public static DeleteResultVO from(DeleteResultBO bo) {
        return DeleteResultVO.builder()
                .examNo(bo.getExamNo())
                .deletedRecordCount(bo.getDeletedRecordCount())
                .minioPath(bo.getMinioPath())
                .deletedEdgeCount(bo.getDeletedEdgeCount())
                .build();
    }
}