package com.graphnexus.application.file.textbook.service;

import com.graphnexus.application.file.textbook.model.TextbookBO;
import com.graphnexus.application.file.textbook.model.ParseResult;
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
    TextbookBO upload(MultipartFile file, String subject);

    /** 解析教材文件（从 MinIO 读取 → 文本提取 → 入库）。 */
    ParseResult parse(Long documentId);

    /** 分页查询教材列表（条件筛选）。 */
    Page<TextbookBO> listTextBooks(int pageNum, int pageSize, String fileType, String name, String subject);

    /** 按 ID 查询单个教材。 */
    TextbookBO getTextBook(Long id);

    /** 逻辑删除教材 + MinIO 文件清除 + Neo4j 图谱清理。 */
    void deleteTextBook(Long id);

    /**
     * 级联删除终结点：图谱清理完成后，执行 MinIO 文件删除 + MySQL 物理删除。
     *
     * <p>由 {@code TextbookGraphClearedEventListener} 调用（事件链最后一步）。
     * 从 {@code @EventListener} 中抽取为独立 {@code @Transactional} 方法，
     * 确保事务通过 TextbookService 代理正确开启，避免 {@code @EventListener}
     * 适配器绕过 AOP 代理导致 {@code @Transactional} 不生效。</p>
     *
     * @param documentId 待物理删除的文档 ID
     * @param filePath   文件在 MinIO 中的完整访问路径
     */
    void finalizeDeletion(Long documentId, String filePath);

    /**
     * 保存图谱构建/抽取失败原因到文档记录。
     *
     * <p>从 {@code TextbookParsedEventListener} 中抽取为独立 {@code @Transactional} 方法，
     * 消除监听器内 {@code @Transactional} 自调用穿透问题（ADR-028 规则 5+6）。</p>
     *
     * @param documentId  文档 ID
     * @param errorMessage 失败原因（会自动截断）
     */
    void saveFailReason(Long documentId, String errorMessage);
}