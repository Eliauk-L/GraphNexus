package com.graphnexus.application.file.grade.event;

import org.springframework.context.ApplicationEvent;

/**
 * 成绩删除完成事件 — MySQL 物理删除后发布。
 *
 * <p>由 {@code GradeServiceImpl} 发布。
 * 图谱模块监听此事件后清理 Neo4j 中对应 Exam 节点及 ATTENDED/TESTED 边。</p>
 *
 * @author Jay
 * @date 2026/06/19
 */
public class GradeDeletedEvent extends ApplicationEvent {

    private final String examNo;
    private final int recordCount;

    public GradeDeletedEvent(Object source, String examNo, int recordCount) {
        super(source);
        this.examNo = examNo;
        this.recordCount = recordCount;
    }

    public String getExamNo() {
        return examNo;
    }

    public int getRecordCount() {
        return recordCount;
    }
}