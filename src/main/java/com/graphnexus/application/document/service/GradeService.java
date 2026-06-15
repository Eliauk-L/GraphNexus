package com.graphnexus.application.document.service;

import com.graphnexus.application.document.model.DeleteResultBO;
import com.graphnexus.application.document.model.GradeRecordBO;
import com.graphnexus.application.document.model.GradeUploadResultBO;
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
     *
     * @param file    CSV 文件
     * @param subject 所属学科
     * @return 上传结果
     */
    GradeUploadResultBO uploadGradeCsv(MultipartFile file, String subject);

    /**
     * 按考试编号查询未删除的成绩列表。
     *
     * @param examNo 考试编号
     * @return 成绩记录列表
     */
    List<GradeRecordBO> queryByExam(String examNo);

    /**
     * 按考试编号级联删除：MySQL → MinIO → Neo4j 边 → Neo4j 节点 → MySQL 物理删除。
     * 遵循全局删除约束（C1-C5），幂等（C2），使用 is_deleted 中间状态（C3）。
     *
     * @param examNo 考试编号
     * @return 删除结果
     */
    DeleteResultBO deleteByExamNo(String examNo);
}