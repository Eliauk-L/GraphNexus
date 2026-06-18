package com.graphnexus.application.file.grade.event;

import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * 成绩上传完成事件 — CSV 成绩文件解析入库 + Neo4j 图构建完成后发布。
 *
 * <p>由 {@code GradeServiceImpl} 发布。
 * 图模块监听此事件后触发增量融合 + 指标缓存失效。</p>
 *
 * @param examNo          考试编号
 * @param subject         学科
 * @param knowledgePoints 涉及的知识点名称列表
 * @author Jay
 * @date 2026/06/18
 */
public class GradeUploadedEvent extends ApplicationEvent {

    private final String examNo;
    private final String subject;
    private final List<String> knowledgePoints;

    public GradeUploadedEvent(Object source, String examNo, String subject, List<String> knowledgePoints) {
        super(source);
        this.examNo = examNo;
        this.subject = subject;
        this.knowledgePoints = knowledgePoints;
    }

    public String getExamNo() {
        return examNo;
    }

    public String getSubject() {
        return subject;
    }

    public List<String> getKnowledgePoints() {
        return knowledgePoints;
    }
}