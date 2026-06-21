package com.graphnexus.application.file.textbook.event;

import org.springframework.context.ApplicationEvent;

/**
 * 教材解析完成事件 — 解析成功（PARSED）后发布，由图谱构建模块监听并触发构建流水线。
 *
 * <p>与成绩上传事件驱动模式一致（{@code GradeUploadedEvent} → {@code GradeGraphEventListener}），
 * 教材模块不直接依赖图谱模块，仅发布事件。</p>
 *
 * @param documentId 已完成解析的文档 ID
 * @author Jay
 * @date 2026/06/21
 */
public class TextbookParsedEvent extends ApplicationEvent {

    private final Long documentId;

    public TextbookParsedEvent(Object source, Long documentId) {
        super(source);
        this.documentId = documentId;
    }

    public Long getDocumentId() {
        return documentId;
    }
}