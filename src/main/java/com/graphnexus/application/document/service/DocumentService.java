package com.graphnexus.application.document.service;

import com.graphnexus.application.document.model.DeleteResultBO;
import com.graphnexus.application.document.model.DocumentBO;
import com.graphnexus.application.document.model.GradeUploadResultBO;
import com.graphnexus.application.document.model.ParseResult;
import com.graphnexus.application.document.model.UpdateDocumentBO;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档处理业务服务接口（L2 应用层）。
 *
 * @author Jay
 * @date 2026/06/12
 */
public interface DocumentService {

    /**
     * 上传 PDF 文件并存入 MinIO + MySQL。
     *
     * @param file    PDF 文件
     * @param subject 所属学科
     * @return 文档业务对象
     */
    DocumentBO upload(MultipartFile file, String subject);

    /**
     * 触发 PDF 解析。
     *
     * @param documentId 文档 ID
     * @return 解析结果
     */
    ParseResult process(Long documentId);

    /**
     * 分页查询文档列表（仅未删除）。
     *
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页结果
     */
    Page<DocumentBO> listDocuments(int pageNum, int pageSize);

    /**
     * 按 ID 查询单个文档。
     *
     * @param id 文档 ID
     * @return 文档业务对象
     */
    DocumentBO getDocument(Long id);

    /**
     * 更新文档名称。
     *
     * @param id 文档 ID
     * @param bo 更新内容
     * @return 更新后的文档
     */
    DocumentBO updateDocument(Long id, UpdateDocumentBO bo);

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
    java.util.List<com.graphnexus.application.document.model.GradeRecordBO> queryGradeByExam(String examNo);
}