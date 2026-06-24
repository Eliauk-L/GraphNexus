package com.graphnexus.application.graph.construction.listener;

import com.graphnexus.application.file.grade.event.GradeDeletedEvent;
import com.graphnexus.application.file.grade.event.GradeUploadedEvent;
import com.graphnexus.application.graph.construction.event.GraphConstructedEvent;
import com.graphnexus.common.event.GraphChangedEvent;
import com.graphnexus.infrastructure.mysql.file.entity.ExamRecordDO;
import com.graphnexus.infrastructure.mysql.file.repository.ExamRecordRepository;
import com.graphnexus.infrastructure.neo4j.edge.AttendedEdge;
import com.graphnexus.infrastructure.neo4j.edge.BelongsToSubjectEdge;
import com.graphnexus.infrastructure.neo4j.edge.TestedEdge;
import com.graphnexus.infrastructure.neo4j.node.ExamNode;
import com.graphnexus.infrastructure.neo4j.node.KnowledgePointNode;
import com.graphnexus.infrastructure.neo4j.node.StudentNode;
import com.graphnexus.infrastructure.neo4j.node.SubjectNode;
import com.graphnexus.infrastructure.neo4j.repository.ConstructionGraphRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 成绩事件 → Neo4j 图谱操作监听器。
 *
 * <p>监听成绩上传/删除事件，负责图谱节点的创建和清理。
 * 与成绩处理模块完全解耦，仅依赖 MySQL（只读）+ Neo4j（写入）。
 * 构建完成后发布 GraphConstructedEvent 触发下游融合。</p>
 *
 * @author Jay
 * @date 2026/06/19
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GradeGraphEventListener {

    private final ExamRecordRepository examRecordRepository;
    private final ConstructionGraphRepository constructionGraphRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 成绩上传 → 构建 Neo4j 图。
     */
    @EventListener
    public void onGradeUploaded(GradeUploadedEvent event) {
        String examNo = event.getExamNo();
        String subject = event.getSubject();

        List<ExamRecordDO> records = examRecordRepository.findByExamNo(examNo);
        if (records.isEmpty()) {
            log.warn("收到 GradeUploadedEvent 但 MySQL 中无记录: examNo={}", examNo);
            return;
        }

        ExamRecordDO first = records.get(0);

        // ① 查找或创建 SubjectNode + BELONGS_TO_SUBJECT 边
        SubjectNode subjectNode = constructionGraphRepository.findOrCreateSubject(subject);

        // ② MERGE ExamNode + BELONGS_TO_SUBJECT
        ExamNode examNode = new ExamNode(examNo, first.getExamName(), first.getExamDate());
        examNode = constructionGraphRepository.save(examNode);
        constructionGraphRepository.saveEdge(new BelongsToSubjectEdge(examNode.getId(), subjectNode.getId()));

        // ③ MERGE StudentNode（按 studentNo 去重）+ AttendedEdge
        for (ExamRecordDO rec : records) {
            StudentNode studentNode = constructionGraphRepository.findOrCreateStudent(
                    rec.getStudentNo(), rec.getName(), rec.getClassName(), null);
            constructionGraphRepository.saveEdge(new AttendedEdge(studentNode.getId(), examNode.getId()));
        }

        // ④ MERGE KnowledgePointNode（按 name+Subject 去重）+ TestedEdge
        // BELONGS_TO_SUBJECT 边由 findOrCreateKnowledgePoint 内部 MERGE 创建，无需重复建边
        for (String kpName : event.getKnowledgePoints()) {
            KnowledgePointNode kpNode = constructionGraphRepository.findOrCreateKnowledgePoint(
                    kpName, subjectNode.getId());
            constructionGraphRepository.saveEdge(new TestedEdge(examNode.getId(), kpNode.getId()));
        }

        // 发布 GraphConstructedEvent（构建完成后触发融合 · DESIGN D5）
        eventPublisher.publishEvent(new GraphConstructedEvent(
                this, GraphConstructedEvent.SOURCE_CSV, GraphConstructedEvent.MODE_FULL,
                event.getSubject(), event.getKnowledgePoints(), null, event.getExamNo()));

        // 图结构已变更（新节点/边已写入），触发指标缓存失效
        eventPublisher.publishEvent(new GraphChangedEvent(this));

        log.info("图谱构建完成: examNo={}, students={}, kps={}",
                examNo, records.size(), event.getKnowledgePoints().size());
    }

    /**
     * 成绩删除 → 清理 Neo4j 图。
     *
     * <p>级联清理顺序：查 KP 名称 → ATTENDED 边 → TESTED 边 → ExamNode
     * → 孤点 StudentNode → 孤点 KnowledgePoint。
     * KP 名称在删边前取出，用于后续孤点检查。</p>
     */
    @EventListener
    public void onGradeDeleted(GradeDeletedEvent event) {
        String examNo = event.getExamNo();

        // 在删边前查出关联的知识点名称
        List<String> kpNames = constructionGraphRepository.findKpNamesByExamNo(examNo);

        int attendEdges = constructionGraphRepository.deleteEdgesByExamNo(examNo, "ATTENDED");
        int testedEdges = constructionGraphRepository.deleteEdgesByExamNo(examNo, "TESTED");
        int deletedNodes = constructionGraphRepository.deleteExamNode(examNo);

        // 级联清理孤点 StudentNode
        int orphanStudentsDeleted = 0;
        for (String studentNo : event.getStudentNos()) {
            if (examRecordRepository.findByStudentNo(studentNo).isEmpty()) {
                constructionGraphRepository.deleteOrphanStudent(studentNo);
                orphanStudentsDeleted++;
            }
        }

        // 级联清理孤点 KnowledgePoint（无 TESTED + 无 ALIGNED_TO 才删，防止误删文档图谱节点）
        int orphanKpsDeleted = 0;
        for (String kpName : kpNames) {
            int deleted = constructionGraphRepository.deleteOrphanKnowledgePoint(kpName);
            if (deleted > 0) orphanKpsDeleted++;
        }

        // 图结构已变更（节点/边已删除），触发指标缓存失效
        eventPublisher.publishEvent(new GraphChangedEvent(this));

        log.info("图谱清理完成: examNo={}, attendEdges={}, testedEdges={}, examNodes={}, orphanStudents={}, orphanKps={}",
                examNo, attendEdges, testedEdges, deletedNodes, orphanStudentsDeleted, orphanKpsDeleted);
    }
}