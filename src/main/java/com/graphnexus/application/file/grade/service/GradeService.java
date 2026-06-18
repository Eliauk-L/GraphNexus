package com.graphnexus.application.file.grade.service;

import com.graphnexus.application.file.textbook.model.DeleteResultBO;
import com.graphnexus.application.file.grade.model.GradeRecordBO;
import com.graphnexus.application.file.grade.model.GradeUploadResultBO;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 成绩处理业务服务接口（L2 应用层）。
 *
 * @author Jay
 * @date 2026/06/15
 */
public interface GradeService {

    /**
     * 上传 CSV 成绩文件，全链路同步处理。
     */
    GradeUploadResultBO uploadGradeCsv(MultipartFile file, String subject);

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