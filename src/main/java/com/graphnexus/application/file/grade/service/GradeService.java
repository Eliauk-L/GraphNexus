package com.graphnexus.application.file.grade.service;

import com.graphnexus.application.file.grade.model.GradeRecordBO;
import com.graphnexus.common.PageResult;

/**
 * 成绩业务服务接口（L2 应用层）。
 *
 * <p>上传由 {@link GradeUploadService} 独立负责，本接口仅含条件查询与删除。</p>
 *
 * @author Jay
 * @date 2026/06/19
 */
public interface GradeService {

    /**
     * 条件组合查询成绩记录（所有参数可选，支持分页）。
     */
    PageResult<GradeRecordBO> queryByConditions(
            String examNo, String examName,
            String studentNo, String name,
            String className, String subject,
            int pageNum, int pageSize);

    /**
     * 按考试编号级联删除（MySQL 物理删除 + 发布 GradeDeletedEvent）。
     *
     * @return [examNo, deletedRecordCount]
     */
    Object[] deleteByExamNo(String examNo);
}