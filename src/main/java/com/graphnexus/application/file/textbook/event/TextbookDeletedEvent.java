package com.graphnexus.application.file.textbook.event;

import org.springframework.context.ApplicationEvent;

/**
 * 教材删除初始化事件 — 进入 DELETING 状态后发布。
 *
 * <p>由 {@code TextbookServiceImpl.deleteTextBook()} 发布。
 * 图谱模块监听此事件后清理 Neo4j 中对应文档的子图和节点，
 * 清理成功后发布 {@link TextbookGraphClearedEvent} 继续级联。</p>
 *
 * <p>事件驱动单向依赖：textbook → graph → textbook（回调），
 * 教材模块不直接依赖图谱模块。</p>
 *
 * @param documentId  待删除的教材文档 ID
 * @param filePath    文件在 MinIO 中的完整访问路径（用于后续文件清理）
 * @param documentNo  文件内容 MD5（用于 MinIO 对象 key 和引用计数）
 * @author Jay
 * @date 2026/06/21
 */
public class TextbookDeletedEvent extends ApplicationEvent {

    private final Long documentId;
    private final String filePath;
    private final String documentNo;

    public TextbookDeletedEvent(Object source, Long documentId, String filePath, String documentNo) {
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