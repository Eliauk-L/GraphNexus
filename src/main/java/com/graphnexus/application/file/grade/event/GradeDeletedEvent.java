package com.graphnexus.application.file.grade.event;

import org.springframework.context.ApplicationEvent;

import java.util.Collections;
import java.util.List;

/**
 * 成绩删除完成事件 — MySQL 物理删除后发布。
 *
 * <p>由 {@code GradeServiceImpl} 发布。
 * 图谱模块监听此事件后清理 Neo4j 中对应 Exam 节点、ATTENDED/TESTED 边，
 * 并检查受影响的学生是否还有剩余考试记录，若无则级联删除孤点 StudentNode。</p>
 *
 * @author Jay
 * @date 2026/06/19
 */
public class GradeDeletedEvent extends ApplicationEvent {

    private final String examNo;
    private final int recordCount;
    private final List<String> studentNos;

    public GradeDeletedEvent(Object source, String examNo, int recordCount) {
        this(source, examNo, recordCount, Collections.emptyList());
    }

    public GradeDeletedEvent(Object source, String examNo, int recordCount, List<String> studentNos) {
        super(source);
        this.examNo = examNo;
        this.recordCount = recordCount;
        this.studentNos = studentNos != null ? studentNos : Collections.emptyList();
    }

    public String getExamNo() {
        return examNo;
    }

    public int getRecordCount() {
        return recordCount;
    }

    public List<String> getStudentNos() {
        return studentNos;
    }
}