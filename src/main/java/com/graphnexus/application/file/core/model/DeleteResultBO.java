package com.graphnexus.application.file.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 级联删除结果 BO（L2 层）。
 *
 * @author Jay
 * @date 2026/06/15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteResultBO {

    /** 考试编号 */
    private String examNo;

    /** 已删除的 MySQL 记录数 */
    private int deletedRecordCount;

    /** 已删除的 MinIO 文件路径 */
    private String filePath;

    /** 已删除的 Neo4j 边数 */
    private int deletedEdgeCount;
}