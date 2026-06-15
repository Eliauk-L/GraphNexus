package com.graphnexus.infrastructure.neo4j.node;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Node;

import java.util.Map;

/**
 * Neo4j 学生节点 — CSV 成绩导入的学生实体。
 *
 * <p>以 {@code studentNo}（学号）做 MERGE key 实现跨考试复用，
 * 通过 ATTENDED 边连接到 Exam 节点。见 DESIGN §2.3。</p>
 *
 * @author Jay
 * @date 2026/06/15
 */
@Data
@Node("Student")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class StudentNode extends GraphNode {

    /** 学号（唯一标识，MERGE key） */
    private String studentNo;

    /** 学生姓名 */
    private String name;

    /** 班级名称 */
    private String className;

    /** 年级/学段 */
    private String grade;

    public StudentNode(String studentNo, String name, String className, String grade) {
        super(NodeType.STUDENT.getLabel());
        this.studentNo = studentNo;
        this.name = name;
        this.className = className;
        this.grade = grade;
    }

    @Override
    public Map<String, Object> toProperties() {
        Map<String, Object> props = super.toProperties();
        props.put("studentNo", this.getStudentNo());
        props.put("name", this.getName());
        props.put("className", this.getClassName());
        props.put("grade", this.getGrade());
        return props;
    }
}