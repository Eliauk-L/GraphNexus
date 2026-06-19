package com.graphnexus.application.file.grade.service;

import com.graphnexus.application.file.textbook.model.DeleteResultBO;
import com.graphnexus.application.file.grade.model.GradeRecordBO;
import com.graphnexus.application.file.grade.model.GradeUploadResultBO;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 成绩业务服务接口（L2 应用层）。
 *
 * <p>上传由 {@link GradeUploadService} 独立负责，本接口仅含查询与删除。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
public interface GradeService {

    /**
     * 分页查询成绩列表（按考试编号分组）。
     */
    Page<GradeUploadResultBO> listExams(int pageNum, int pageSize);

    /**
     * 按考试编号查询未删除的成绩列表。
     */
    List<GradeRecordBO> queryByExam(String examNo);

    /**
     * 按考试编号级联删除。
     */
    DeleteResultBO deleteByExamNo(String examNo);
}