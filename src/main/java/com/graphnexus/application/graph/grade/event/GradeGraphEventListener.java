package com.graphnexus.application.graph.grade.event;

import com.graphnexus.application.file.grade.event.GradeDeletedEvent;
import com.graphnexus.application.file.grade.event.GradeUploadedEvent;
import com.graphnexus.infrastructure.mysql.file.entity.ExamRecordDO;
import com.graphnexus.infrastructure.mysql.file.repository.ExamRecordRepository;
import com.graphnexus.infrastructure.neo4j.edge.AttendedEdge;
import com.graphnexus.infrastructure.neo4j.edge.TestedEdge;
import com.graphnexus.infrastructure.neo4j.node.ExamNode;
import com.graphnexus.infrastructure.neo4j.node.KnowledgePointNode;
import com.graphnexus.infrastructure.neo4j.node.StudentNode;
import com.graphnexus.infrastructure.neo4j.repository.GraphNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 成绩事件 → Neo4j 图谱操作监听器。
 *
 * <p>监听成绩上传/删除事件，负责图谱节点的创建和清理。
 * 与成绩处理模块完全解耦，仅依赖 MySQL（只读）+ Neo4j（写入）。
 * @Order(1) 确保在图谱融合之前完成构建。</p>
 *
 * @author Jay
 * @date 2026/06/19
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GradeGraphEventListener {

    private final ExamRecordRepository examRecordRepository;
    private final GraphNodeRepository graphNodeRepository;

    /**
     * 成绩上传 → 构建 Neo4j 图。
     */
    @EventListener
    @Order(1)
    public void onGradeUploaded(GradeUploadedEvent event) {
        String examNo = event.getExamNo();
        String subject = event.getSubject();

        List<ExamRecordDO> records = examRecordRepository.findByExamNoAndIsDeleted(examNo, 0);
        if (records.isEmpty()) {
            log.warn("收到 GradeUploadedEvent 但 MySQL 中无记录: examNo={}", examNo);
            return;
        }

        ExamRecordDO first = records.get(0);

        // ① MERGE ExamNode
        ExamNode examNode = new ExamNode(examNo, first.getExamName(), first.getExamDate(), subject);
        examNode = graphNodeRepository.save(examNode);

        // ② MERGE StudentNode + AttendedEdge
        for (ExamRecordDO rec : records) {
            StudentNode studentNode = new StudentNode(
                    rec.getStudentNo(), rec.getName(), rec.getClassName(), null);
            studentNode = graphNodeRepository.save(studentNode);
            graphNodeRepository.saveEdge(new AttendedEdge(studentNode.getId(), examNode.getId()));
        }

        // ③ MERGE KnowledgePointNode + TestedEdge
        for (String kpName : event.getKnowledgePoints()) {
            KnowledgePointNode kpNode = new KnowledgePointNode(kpName, subject);
            kpNode = graphNodeRepository.save(kpNode);
            graphNodeRepository.saveEdge(new TestedEdge(examNode.getId(), kpNode.getId()));
        }

        log.info("图谱构建完成: examNo={}, students={}, kps={}",
                examNo, records.size(), event.getKnowledgePoints().size());
    }

    /**
     * 成绩删除 → 清理 Neo4j 图。
     */
    @EventListener
    public void onGradeDeleted(GradeDeletedEvent event) {
        String examNo = event.getExamNo();

        int attendEdges = graphNodeRepository.deleteEdgesByExamNo(examNo, "ATTENDED");
        int testedEdges = graphNodeRepository.deleteEdgesByExamNo(examNo, "TESTED");
        int deletedNodes = graphNodeRepository.deleteExamNode(examNo);

        log.info("图谱清理完成: examNo={}, attendEdges={}, testedEdges={}, examNodes={}",
                examNo, attendEdges, testedEdges, deletedNodes);
    }
}