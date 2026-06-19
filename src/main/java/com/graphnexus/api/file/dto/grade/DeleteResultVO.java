package com.graphnexus.api.file.dto.grade;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 级联删除结果 VO（L1 返回前端，对应 AC-9 响应体）。
 *
 * @author Jay
 * @date 2026/06/19
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

    public static DeleteResultVO of(String examNo, int deletedRecordCount) {
        return DeleteResultVO.builder()
                .examNo(examNo)
                .deletedRecordCount(deletedRecordCount)
                .build();
    }
}