package com.graphnexus.infrastructure.neo4j.node;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Node;

import java.util.Map;

/**
 * Neo4j 学科节点 — 标准化学科实体（如"数学""物理""英语"）。
 *
 * <p>取代原 KnowledgePoint/Exam/FileNode 上的 {@code subject} 字符串属性。
 * 各节点通过 BELONGS_TO_SUBJECT 边指向 SubjectNode，融合分组基于 Subject 节点引用。
 * 预留 {@code (:Subject)-[:CHILD_OF]->(:Subject)} 学科层级扩展（v2）。</p>
 *
 * @author Jay
 * @date 2026/06/20
 */
@Data
@Node("Subject")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SubjectNode extends GraphNode {

    /** 学科名称（唯一标识，如"数学""物理""英语"） */
    private String name;

    public SubjectNode(String name) {
        super(NodeType.SUBJECT.getLabel());
        this.name = name;
    }

    @Override
    public Map<String, Object> toProperties() {
        Map<String, Object> props = super.toProperties();
        props.put("name", this.getName());
        return props;
    }
}