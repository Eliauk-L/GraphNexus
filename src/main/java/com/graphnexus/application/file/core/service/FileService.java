package com.graphnexus.application.file.core.service;

import com.graphnexus.application.file.core.model.FileBO;
import com.graphnexus.application.file.parse.model.ParseResult;
import com.graphnexus.application.file.core.model.UpdateFileBO;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档处理业务服务接口（L2 应用层）— 纯文档职责。
 *
 * @author Jay
 * @date 2026/06/12
 */
public interface FileService {

    /**
     * 上传文档文件（委托 DocumentProcessingPipeline 全链路处理）。
     */
    FileBO upload(MultipartFile file, String subject);

    /**
     * 手动触发文档处理（委托 DocumentProcessingPipeline 断点续跑）。
     */
    ParseResult process(Long documentId);

    /**
     * 分页查询文档列表（条件筛选）。
     */
    Page<FileBO> listDocuments(int pageNum, int pageSize, String fileType, String name);

    /**
     * 按 ID 查询单个文档。
     */
    FileBO getDocument(Long id);

    /**
     * 更新文档名称。
     */
    FileBO updateDocument(Long id, UpdateFileBO bo);

    /**
     * 逻辑删除文档 + MinIO 文件清除 + Neo4j 图谱清理。
     */
    void deleteDocument(Long id);
}