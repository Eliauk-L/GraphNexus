package com.graphnexus.infrastructure.neo4j.node;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.LocalDate;
import java.util.Map;

/**
 * Neo4j 考试节点 — CSV 成绩导入的考试实体。
 *
 * <p>以 {@code examNo}（考试编号）做 MERGE key，通过 ATTENDED / TESTED 边关联 Student 和 KnowledgePoint。
 * 学科信息通过 BELONGS_TO_SUBJECT 边关联 SubjectNode（见 ADR-019）。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
@Data
@Node("Exam")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ExamNode extends GraphNode {

    /** 考试编号（唯一标识，MERGE key） */
    private String examNo;

    /** 考试名称（如"九年级第一次月考"） */
    private String name;

    /** 考试日期 */
    private LocalDate examDate;

    public ExamNode(String examNo, String name, LocalDate examDate) {
        super(NodeType.EXAM.getLabel());
        this.examNo = examNo;
        this.name = name;
        this.examDate = examDate;
    }

    @Override
    public Map<String, Object> toProperties() {
        Map<String, Object> props = super.toProperties();
        props.put("examNo", this.getExamNo());
        props.put("name", this.getName());
        props.put("examDate", this.getExamDate() != null ? this.getExamDate().toString() : null);
        return props;
    }
}