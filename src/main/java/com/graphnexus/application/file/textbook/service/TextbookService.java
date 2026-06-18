package com.graphnexus.application.file.textbook.service;

import com.graphnexus.application.file.textbook.model.FileBO;
import com.graphnexus.application.file.parse.ParseResult;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

/**
 * 教材业务服务接口（L2 应用层）。
 *
 * @author Jay
 * @date 2026/06/12
 */
public interface TextbookService {

    /** 上传教材文件（仅存储入库）。 */
    FileBO upload(MultipartFile file, String subject);

    /** 触发教材处理链路（解析→抽取→融合）。 */
    ParseResult process(Long documentId);

    /** 分页查询教材列表（条件筛选）。 */
    Page<FileBO> listDocuments(int pageNum, int pageSize, String fileType, String name);

    /** 按 ID 查询单个教材。 */
    FileBO getDocument(Long id);

    /** 逻辑删除教材 + MinIO 文件清除 + Neo4j 图谱清理。 */
    void deleteDocument(Long id);
}