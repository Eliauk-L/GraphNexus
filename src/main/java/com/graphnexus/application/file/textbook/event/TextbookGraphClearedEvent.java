package com.graphnexus.application.file.textbook.event;

import org.springframework.context.ApplicationEvent;

/**
 * 教材图谱清理完成事件 — Neo4j 子图删除成功后由图谱模块发布。
 *
 * <p>由 {@code TextbookDeletedEventListener}（图谱模块）发布。
 * 教材模块监听此事件后执行 MinIO 文件删除 + MySQL 物理删除，
 * 完成级联删除的最后一步。</p>
 *
 * <p>此事件定义在教材模块中（教材域概念），由图谱模块发布（跨模块事件驱动）。</p>
 *
 * @param documentId  已清理图谱的教材文档 ID
 * @param filePath    文件在 MinIO 中的完整访问路径
 * @param documentNo  文件内容 MD5
 * @author Jay
 * @date 2026/06/21
 */
public class TextbookGraphClearedEvent extends ApplicationEvent {

    private final Long documentId;
    private final String filePath;
    private final String documentNo;

    public TextbookGraphClearedEvent(Object source, Long documentId, String filePath, String documentNo) {
        super(source);
        this.documentId = documentId;
        this.filePath = filePath;
        this.documentNo = documentNo;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getDocumentNo() {
        return documentNo;
    }
}