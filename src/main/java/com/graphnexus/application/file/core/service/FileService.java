package com.graphnexus.application.file.core.service;

import com.graphnexus.application.file.core.model.DeleteResultBO;
import com.graphnexus.application.file.core.model.FileBO;
import com.graphnexus.application.file.upload.model.GradeUploadResultBO;
import com.graphnexus.application.file.parse.model.ParseResult;
import com.graphnexus.application.file.core.model.UpdateFileBO;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档处理业务服务接口（L2 应用层）。
 *
 * @author Jay
 * @date 2026/06/12
 */
public interface FileService {

    /**
     * 上传 PDF 文件并存入 MinIO + MySQL。
     *
     * @param file    PDF 文件
     * @param subject 所属学科
     * @return 文档业务对象
     */
    FileBO upload(MultipartFile file, String subject);

    /**
     * 触发 PDF 解析。
     *
     * @param documentId 文档 ID
     * @return 解析结果
     */
    ParseResult process(Long documentId);

    /**
     * 分页查询文档列表（条件筛选）。
     *
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页大小
     * @param fileType 文件类型筛选（可选，null 表示不过滤）
     * @param name     文件名模糊搜索（可选，null 表示不过滤）
     * @return 分页结果
     */
    Page<FileBO> listDocuments(int pageNum, int pageSize, String fileType, String name);

    /**
     * 按 ID 查询单个文档。
     *
     * @param id 文档 ID
     * @return 文档业务对象
     */
    FileBO getDocument(Long id);

    /**
     * 更新文档名称。
     *
     * @param id 文档 ID
     * @param bo 更新内容
     * @return 更新后的文档
     */
    FileBO updateDocument(Long id, UpdateFileBO bo);

    /**
     * 逻辑删除文档 + MinIO 文件清除。
     *
     * @param id 文档 ID
     */
    void deleteDocument(Long id);

    /**
     * 上传 CSV 成绩文件（委托 GradeService）。
     *
     * @param file    CSV 文件
     * @param subject 所属学科
     * @return 上传结果
     */
    GradeUploadResultBO uploadGradeCsv(MultipartFile file, String subject);

    /**
     * 按考试编号级联删除成绩（委托 GradeService）。
     *
     * @param examNo 考试编号
     * @return 删除结果
     */
    DeleteResultBO deleteGradeByExamNo(String examNo);

    /**
     * 按考试编号查询成绩列表（委托 GradeService）。
     *
     * @param examNo 考试编号
     * @return 成绩记录列表
     */
    java.util.List<com.graphnexus.application.file.upload.model.GradeRecordBO> queryGradeByExam(String examNo);
}